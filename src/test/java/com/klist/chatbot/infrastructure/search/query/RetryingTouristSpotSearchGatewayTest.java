package com.klist.chatbot.infrastructure.search.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.klist.chatbot.search.application.TouristSpotSearchCriteria;
import com.klist.chatbot.search.application.TouristSpotSearchGateway;
import com.klist.chatbot.search.application.TouristSpotSearchResult;
import com.klist.chatbot.observability.RetryEventListener;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetryingTouristSpotSearchGatewayTest {

    private final TouristSpotSearchGateway delegate = mock(TouristSpotSearchGateway.class);
    private final TouristSpotSearchCriteria criteria = new TouristSpotSearchCriteria(
            "서울", null, null, null, null, null, null, null, null,
            null, null, null, 5, null
    );

    @Test
    void retriesTransientFailuresWithExponentialBackoff() {
        TouristSpotSearchException failure = failure(true);
        TouristSpotSearchResult success = new TouristSpotSearchResult(
                List.of(), 0, Duration.ZERO
        );
        when(delegate.search(criteria)).thenThrow(failure).thenThrow(failure).thenReturn(success);
        List<Duration> sleeps = new ArrayList<>();
        RetryEventListener retryEvents = mock(RetryEventListener.class);
        RetryingTouristSpotSearchGateway gateway = gateway(sleeps::add, retryEvents);

        assertThat(gateway.search(criteria)).isSameAs(success);
        assertThat(sleeps).containsExactly(Duration.ofMillis(100), Duration.ofMillis(200));
        verify(delegate, times(3)).search(criteria);
        verify(retryEvents).retrying("elasticsearch", "transient", 2, Duration.ofMillis(100));
        verify(retryEvents).retrying("elasticsearch", "transient", 3, Duration.ofMillis(200));
    }

    @Test
    void doesNotRetryPermanentFailure() {
        TouristSpotSearchException failure = failure(false);
        when(delegate.search(criteria)).thenThrow(failure);
        RetryingTouristSpotSearchGateway gateway = gateway(duration -> { });

        assertThatThrownBy(() -> gateway.search(criteria)).isSameAs(failure);
        verify(delegate).search(criteria);
    }

    @Test
    void stopsAfterMaximumAttempts() {
        TouristSpotSearchException failure = failure(true);
        when(delegate.search(criteria)).thenThrow(failure);
        RetryEventListener retryEvents = mock(RetryEventListener.class);
        RetryingTouristSpotSearchGateway gateway = gateway(duration -> { }, retryEvents);

        assertThatThrownBy(() -> gateway.search(criteria)).isSameAs(failure);
        verify(delegate, times(3)).search(criteria);
        verify(retryEvents).exhausted("elasticsearch", "transient", 3);
    }

    @Test
    void doesNotStartBackoffWhenRequestTimeoutBudgetIsExhausted() {
        TouristSpotSearchException failure = failure(true);
        when(delegate.search(criteria)).thenThrow(failure);
        List<Duration> sleeps = new ArrayList<>();
        RetryEventListener retryEvents = mock(RetryEventListener.class);
        RetryingTouristSpotSearchGateway gateway = new RetryingTouristSpotSearchGateway(
                delegate,
                3,
                Duration.ofMillis(100),
                Duration.ofSeconds(1),
                sleeps::add,
                retryEvents,
                () -> 0L
        );

        assertThatThrownBy(() -> gateway.search(criteria, Duration.ofMillis(100)))
                .isSameAs(failure);
        assertThat(sleeps).isEmpty();
        verify(delegate).search(criteria);
        verify(retryEvents).exhausted("elasticsearch", "timeout_budget", 1);
    }

    private RetryingTouristSpotSearchGateway gateway(SearchRetrySleeper sleeper) {
        return gateway(sleeper, RetryEventListener.NO_OP);
    }

    private RetryingTouristSpotSearchGateway gateway(
            SearchRetrySleeper sleeper,
            RetryEventListener retryEvents
    ) {
        return new RetryingTouristSpotSearchGateway(
                delegate, 3, Duration.ofMillis(100), Duration.ofSeconds(1), sleeper,
                retryEvents
        );
    }

    private static TouristSpotSearchException failure(boolean retryable) {
        return new TouristSpotSearchException("search failed", null, retryable);
    }
}
