package com.klist.chatbot.infrastructure.tourapi.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.klist.chatbot.infrastructure.tourapi.dto.TourApiDetailCommonItem;
import com.klist.chatbot.observability.RetryEventListener;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RetryingRateLimitedTourApiClientTest {

    private final TourApiClient delegate = mock(TourApiClient.class);
    private final RetryEventListener retryEvents = mock(RetryEventListener.class);
    private final AtomicLong nanoTime = new AtomicLong();
    private final List<Duration> sleeps = new ArrayList<>();
    private RetryingRateLimitedTourApiClient client;

    @BeforeEach
    void setUp() {
        client = new RetryingRateLimitedTourApiClient(
                delegate,
                Duration.ofMillis(100),
                3,
                Duration.ofMillis(200),
                Duration.ofSeconds(2),
                nanoTime::get,
                duration -> {
                    sleeps.add(duration);
                    nanoTime.addAndGet(duration.toNanos());
                },
                retryEvents
        );
    }

    @Test
    void enforcesMinimumIntervalBetweenSuccessfulCalls() {
        TourApiClientResult<TourApiDetailCommonItem> success = success();
        when(delegate.getDetailCommon("1")).thenReturn(success);
        when(delegate.getDetailCommon("2")).thenReturn(success);

        client.getDetailCommon("1");
        client.getDetailCommon("2");

        assertThat(sleeps).containsExactly(Duration.ofMillis(100));
    }

    @Test
    void retriesTransientFailuresWithExponentialBackoff() {
        when(delegate.getDetailCommon("1"))
                .thenReturn(TourApiClientResult.failure("timeout: request timed out"))
                .thenReturn(TourApiClientResult.failure("http error: status 503"))
                .thenReturn(success());

        assertThat(client.getDetailCommon("1").isSuccess()).isTrue();

        assertThat(sleeps).containsExactly(Duration.ofMillis(200), Duration.ofMillis(400));
        verify(retryEvents).retrying("tourapi", "timeout", 2, Duration.ofMillis(200));
        verify(retryEvents).retrying("tourapi", "http_error", 3, Duration.ofMillis(400));
    }

    @Test
    void recordsExhaustionAfterLastRetryableFailure() {
        TourApiClientResult<TourApiDetailCommonItem> failure =
                TourApiClientResult.failure("connection: refused");
        when(delegate.getDetailCommon("1")).thenReturn(failure);

        assertThat(client.getDetailCommon("1")).isSameAs(failure);

        verify(retryEvents).exhausted("tourapi", "connection", 3);
    }

    @Test
    void doesNotRetryPermanentOrEmptyResults() {
        TourApiClientResult<TourApiDetailCommonItem> invalid =
                TourApiClientResult.failure("invalid response: malformed body");
        when(delegate.getDetailCommon("1")).thenReturn(invalid);

        assertThat(client.getDetailCommon("1")).isSameAs(invalid);
        verify(retryEvents, never()).retrying(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void validatesRetryPolicy() {
        assertThatThrownBy(() -> new RetryingRateLimitedTourApiClient(
                delegate,
                Duration.ofMillis(-1),
                3,
                Duration.ofMillis(200),
                Duration.ofSeconds(2),
                retryEvents
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryingRateLimitedTourApiClient(
                delegate,
                Duration.ZERO,
                0,
                Duration.ofMillis(200),
                Duration.ofSeconds(2),
                retryEvents
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static TourApiClientResult<TourApiDetailCommonItem> success() {
        return TourApiClientResult.success(mock(TourApiDetailCommonItem.class));
    }
}
