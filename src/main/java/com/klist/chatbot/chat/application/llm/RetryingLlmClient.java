package com.klist.chatbot.chat.application.llm;

import com.klist.chatbot.observability.RetryEventListener;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

public class RetryingLlmClient implements LlmClient {

    private final LlmClient delegate;
    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final LongSupplier nanoTime;
    private final LlmRetrySleeper sleeper;
    private final RetryEventListener retryEvents;

    public RetryingLlmClient(
            LlmClient delegate,
            int maxAttempts,
            Duration initialBackoff,
            Duration maxBackoff
    ) {
        this(delegate, maxAttempts, initialBackoff, maxBackoff, System::nanoTime,
                duration -> Thread.sleep(duration.toMillis(), duration.toNanosPart() % 1_000_000),
                RetryEventListener.NO_OP);
    }

    public RetryingLlmClient(
            LlmClient delegate,
            int maxAttempts,
            Duration initialBackoff,
            Duration maxBackoff,
            RetryEventListener retryEvents
    ) {
        this(delegate, maxAttempts, initialBackoff, maxBackoff, System::nanoTime,
                duration -> Thread.sleep(duration.toMillis(), duration.toNanosPart() % 1_000_000),
                retryEvents);
    }

    RetryingLlmClient(
            LlmClient delegate,
            int maxAttempts,
            Duration initialBackoff,
            Duration maxBackoff,
            LongSupplier nanoTime,
            LlmRetrySleeper sleeper,
            RetryEventListener retryEvents
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
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime must not be null");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper must not be null");
        this.retryEvents = Objects.requireNonNull(retryEvents, "retryEvents must not be null");
    }

    @Override
    public LlmGenerationResult generate(LlmGenerationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        long startedAt = nanoTime.getAsLong();
        LlmClientException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Duration remaining = remaining(request.timeout(), startedAt, lastFailure);
            try {
                LlmGenerationResult result = delegate.generate(
                        new LlmGenerationRequest(request.prompt(), remaining)
                );
                return result.withProcessingTime(elapsed(startedAt));
            } catch (LlmClientException exception) {
                lastFailure = exception;
                if (!exception.retryable() || attempt == maxAttempts) {
                    if (exception.retryable()) {
                        retryEvents.exhausted("openai", reason(exception), attempt);
                    }
                    throw exception;
                }
                Duration backoff = backoff(attempt);
                ensureBackoffFits(request.timeout(), startedAt, backoff, exception);
                retryEvents.retrying("openai", reason(exception), attempt + 1, backoff);
                sleep(backoff, exception);
            }
        }
        throw new IllegalStateException("LLM retry loop completed unexpectedly", lastFailure);
    }

    private Duration remaining(Duration timeout, long startedAt, LlmClientException cause) {
        long elapsed = Math.max(0L, nanoTime.getAsLong() - startedAt);
        long remaining = timeout.toNanos() - elapsed;
        if (remaining <= 0L) {
            throw timeoutFailure(cause);
        }
        return Duration.ofNanos(remaining);
    }

    private Duration elapsed(long startedAt) {
        return Duration.ofNanos(Math.max(0L, nanoTime.getAsLong() - startedAt));
    }

    private void ensureBackoffFits(
            Duration timeout,
            long startedAt,
            Duration backoff,
            LlmClientException cause
    ) {
        Duration remaining = remaining(timeout, startedAt, cause);
        if (remaining.compareTo(backoff) <= 0) {
            throw timeoutFailure(cause);
        }
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

    private void sleep(Duration duration, LlmClientException cause) {
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

    private static LlmClientException timeoutFailure(LlmClientException cause) {
        return new LlmClientException(
                LlmFailureType.TIMEOUT,
                "LLM retry timeout budget exhausted",
                null,
                null,
                true,
                cause
        );
    }

    private static String reason(LlmClientException exception) {
        return exception.failureType().name().toLowerCase(java.util.Locale.ROOT);
    }
}
