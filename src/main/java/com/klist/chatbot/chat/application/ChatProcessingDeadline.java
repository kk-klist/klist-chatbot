package com.klist.chatbot.chat.application;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

final class ChatProcessingDeadline {

    private final long startedAtNanos;
    private final long timeoutNanos;
    private final LongSupplier nanoTime;

    ChatProcessingDeadline(Duration timeout, LongSupplier nanoTime) {
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime must not be null");
        this.timeoutNanos = timeout.toNanos();
        this.startedAtNanos = nanoTime.getAsLong();
    }

    Duration remaining(String completedStage) {
        long elapsedNanos = Math.max(0L, nanoTime.getAsLong() - startedAtNanos);
        long remainingNanos = timeoutNanos - elapsedNanos;
        if (remainingNanos <= 0L) {
            throw new ChatQueryTimeoutException(
                    "Chat processing timed out after " + completedStage
            );
        }
        return Duration.ofNanos(remainingNanos);
    }

    void check(String completedStage) {
        remaining(completedStage);
    }
}
