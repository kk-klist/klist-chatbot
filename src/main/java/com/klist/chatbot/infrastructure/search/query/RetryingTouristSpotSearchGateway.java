package com.klist.chatbot.infrastructure.search.query;

import com.klist.chatbot.search.application.TouristSpotSearchCriteria;
import com.klist.chatbot.search.application.TouristSpotSearchGateway;
import com.klist.chatbot.search.application.TouristSpotSearchResult;
import com.klist.chatbot.observability.RetryEventListener;
import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

public class RetryingTouristSpotSearchGateway implements TouristSpotSearchGateway {

    private final TouristSpotSearchGateway delegate;
    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final SearchRetrySleeper sleeper;
    private final RetryEventListener retryEvents;
    private final LongSupplier nanoTime;

    public RetryingTouristSpotSearchGateway(
            TouristSpotSearchGateway delegate,
            int maxAttempts,
            Duration initialBackoff,
            Duration maxBackoff
    ) {
        this(delegate, maxAttempts, initialBackoff, maxBackoff,
                duration -> Thread.sleep(duration.toMillis(), duration.toNanosPart() % 1_000_000),
                RetryEventListener.NO_OP, System::nanoTime);
    }

    public RetryingTouristSpotSearchGateway(
            TouristSpotSearchGateway delegate,
            int maxAttempts,
            Duration initialBackoff,
            Duration maxBackoff,
            RetryEventListener retryEvents
    ) {
        this(delegate, maxAttempts, initialBackoff, maxBackoff,
                duration -> Thread.sleep(duration.toMillis(), duration.toNanosPart() % 1_000_000),
                retryEvents, System::nanoTime);
    }

    RetryingTouristSpotSearchGateway(
            TouristSpotSearchGateway delegate,
            int maxAttempts,
            Duration initialBackoff,
            Duration maxBackoff,
            SearchRetrySleeper sleeper,
            RetryEventListener retryEvents
    ) {
        this(delegate, maxAttempts, initialBackoff, maxBackoff, sleeper, retryEvents,
                System::nanoTime);
    }

    RetryingTouristSpotSearchGateway(
            TouristSpotSearchGateway delegate,
            int maxAttempts,
            Duration initialBackoff,
            Duration maxBackoff,
            SearchRetrySleeper sleeper,
            RetryEventListener retryEvents,
            LongSupplier nanoTime
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        this.maxAttempts = maxAttempts;
        this.initialBackoff = requirePositive(initialBackoff, "initialBackoff");
        this.maxBackoff = requirePositive(maxBackoff, "maxBackoff");
        if (initialBackoff.compareTo(maxBackoff) > 0) {
            throw new IllegalArgumentException("initialBackoff must not exceed maxBackoff");
        }
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper must not be null");
        this.retryEvents = Objects.requireNonNull(retryEvents, "retryEvents must not be null");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime must not be null");
    }

    @Override
    public TouristSpotSearchResult search(TouristSpotSearchCriteria criteria) {
        return search(criteria, null);
    }

    @Override
    public TouristSpotSearchResult search(
            TouristSpotSearchCriteria criteria,
            Duration timeout
    ) {
        Objects.requireNonNull(criteria, "criteria must not be null");
        if (timeout != null && (timeout.isZero() || timeout.isNegative())) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        long startedAt = nanoTime.getAsLong();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return delegate.search(criteria);
            } catch (TouristSpotSearchException exception) {
                if (!exception.retryable() || attempt == maxAttempts) {
                    if (exception.retryable()) {
                        retryEvents.exhausted("elasticsearch", "transient", attempt);
                    }
                    throw exception;
                }
                Duration backoff = backoff(attempt);
                if (!fits(timeout, startedAt, backoff)) {
                    retryEvents.exhausted("elasticsearch", "timeout_budget", attempt);
                    throw exception;
                }
                retryEvents.retrying("elasticsearch", "transient", attempt + 1, backoff);
                sleep(backoff, exception);
            }
        }
        throw new IllegalStateException("Search retry loop completed unexpectedly");
    }

    private boolean fits(Duration timeout, long startedAt, Duration backoff) {
        if (timeout == null) {
            return true;
        }
        long elapsed = Math.max(0L, nanoTime.getAsLong() - startedAt);
        long remaining = timeout.toNanos() - elapsed;
        return remaining > backoff.toNanos();
    }

    private Duration backoff(int completedAttempts) {
        long multiplier = 1L << Math.min(completedAttempts - 1, 30);
        Duration calculated;
        try {
            calculated = initialBackoff.multipliedBy(multiplier);
        } catch (ArithmeticException exception) {
            calculated = maxBackoff;
        }
        return calculated.compareTo(maxBackoff) <= 0 ? calculated : maxBackoff;
    }

    private void sleep(Duration duration, TouristSpotSearchException cause) {
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            cause.addSuppressed(exception);
            throw cause;
        }
    }

    private static Duration requirePositive(Duration duration, String name) {
        Objects.requireNonNull(duration, name + " must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return duration;
    }
}
