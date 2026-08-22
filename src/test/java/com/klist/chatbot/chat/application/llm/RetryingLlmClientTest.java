package com.klist.chatbot.chat.application.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.klist.chatbot.chat.application.prompt.ChatPrompt;
import com.klist.chatbot.observability.RetryEventListener;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RetryingLlmClientTest {

    private final LlmClient delegate = mock(LlmClient.class);
    private final LlmGenerationRequest request = new LlmGenerationRequest(
            new ChatPrompt("system", "question"),
            Duration.ofSeconds(2)
    );

    @Test
    void retriesRetryableFailuresWithExponentialBackoffAndRemainingTimeout() {
        LlmClientException failure = failure(true);
        LlmGenerationResult success = new LlmGenerationResult("answer", "model", 1, 1);
        when(delegate.generate(org.mockito.ArgumentMatchers.any()))
                .thenThrow(failure)
                .thenThrow(failure)
                .thenReturn(success);
        List<Duration> sleeps = new ArrayList<>();
        RetryEventListener retryEvents = mock(RetryEventListener.class);
        RetryingLlmClient client = client(
                sequentialNanos(0L, 0L, 100_000_000L, 100_000_000L,
                        300_000_000L, 300_000_000L),
                sleeps::add,
                retryEvents
        );

        LlmGenerationResult result = client.generate(request);

        assertThat(result.outputText()).isEqualTo(success.outputText());
        assertThat(result.model()).isEqualTo(success.model());
        assertThat(result.inputTokens()).isEqualTo(success.inputTokens());
        assertThat(result.outputTokens()).isEqualTo(success.outputTokens());
        assertThat(result.processingTime()).isEqualTo(Duration.ofMillis(300));

        assertThat(sleeps).containsExactly(Duration.ofMillis(100), Duration.ofMillis(200));
        ArgumentCaptor<LlmGenerationRequest> requests =
                ArgumentCaptor.forClass(LlmGenerationRequest.class);
        verify(delegate, times(3)).generate(requests.capture());
        assertThat(requests.getAllValues()).extracting(LlmGenerationRequest::timeout)
                .containsExactly(
                        Duration.ofSeconds(2),
                        Duration.ofMillis(1900),
                        Duration.ofMillis(1700)
                );
        verify(retryEvents).retrying("openai", "connection", 2, Duration.ofMillis(100));
        verify(retryEvents).retrying("openai", "connection", 3, Duration.ofMillis(200));
    }

    @Test
    void doesNotRetryNonRetryableFailure() {
        LlmClientException failure = failure(false);
        when(delegate.generate(org.mockito.ArgumentMatchers.any())).thenThrow(failure);
        RetryingLlmClient client = client(() -> 0L, duration -> { });

        assertThatThrownBy(() -> client.generate(request)).isSameAs(failure);
        verify(delegate).generate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void stopsAfterMaximumAttempts() {
        LlmClientException failure = failure(true);
        when(delegate.generate(org.mockito.ArgumentMatchers.any())).thenThrow(failure);
        RetryEventListener retryEvents = mock(RetryEventListener.class);
        RetryingLlmClient client = client(() -> 0L, duration -> { }, retryEvents);

        assertThatThrownBy(() -> client.generate(request)).isSameAs(failure);
        verify(delegate, times(3)).generate(org.mockito.ArgumentMatchers.any());
        verify(retryEvents).exhausted("openai", "connection", 3);
    }

    @Test
    void failsWithTimeoutWhenBackoffDoesNotFitRemainingBudget() {
        LlmClientException failure = failure(true);
        when(delegate.generate(org.mockito.ArgumentMatchers.any())).thenThrow(failure);
        LlmGenerationRequest shortRequest = new LlmGenerationRequest(
                request.prompt(),
                Duration.ofMillis(100)
        );
        RetryingLlmClient client = client(() -> 0L, duration -> { });

        assertThatThrownBy(() -> client.generate(shortRequest))
                .isInstanceOfSatisfying(LlmClientException.class, exception -> {
                    assertThat(exception.failureType()).isEqualTo(LlmFailureType.TIMEOUT);
                    assertThat(exception.getCause()).isSameAs(failure);
                });
        verify(delegate).generate(org.mockito.ArgumentMatchers.any());
    }

    private RetryingLlmClient client(LongSupplier nanoTime, LlmRetrySleeper sleeper) {
        return client(nanoTime, sleeper, RetryEventListener.NO_OP);
    }

    private RetryingLlmClient client(
            LongSupplier nanoTime,
            LlmRetrySleeper sleeper,
            RetryEventListener retryEvents
    ) {
        return new RetryingLlmClient(
                delegate,
                3,
                Duration.ofMillis(100),
                Duration.ofMillis(500),
                nanoTime,
                sleeper,
                retryEvents
        );
    }

    private static LlmClientException failure(boolean retryable) {
        return new LlmClientException(
                LlmFailureType.CONNECTION,
                "failure",
                null,
                null,
                retryable,
                null
        );
    }

    private static LongSupplier sequentialNanos(long... values) {
        AtomicInteger index = new AtomicInteger();
        return () -> values[Math.min(index.getAndIncrement(), values.length - 1)];
    }
}
