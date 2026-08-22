package com.klist.chatbot.infrastructure.tourapi.client;

import com.klist.chatbot.infrastructure.tourapi.collector.TourApiCollectRequest;
import com.klist.chatbot.infrastructure.tourapi.dto.TourApiAreaBasedListItem;
import com.klist.chatbot.infrastructure.tourapi.dto.TourApiCodeItem;
import com.klist.chatbot.infrastructure.tourapi.dto.TourApiDetailCommonItem;
import com.klist.chatbot.infrastructure.tourapi.dto.TourApiDetailImageItem;
import com.klist.chatbot.infrastructure.tourapi.dto.TourApiDetailIntroItem;
import com.klist.chatbot.observability.RetryEventListener;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public class RetryingRateLimitedTourApiClient implements TourApiClient {

    private final TourApiClient delegate;
    private final Duration requestInterval;
    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final LongSupplier nanoTime;
    private final TourApiRetrySleeper sleeper;
    private final RetryEventListener retryEvents;
    private long lastCallStartedAt = -1L;

    public RetryingRateLimitedTourApiClient(
            TourApiClient delegate,
            Duration requestInterval,
            int maxAttempts,
            Duration initialBackoff,
            Duration maxBackoff,
            RetryEventListener retryEvents
    ) {
        this(delegate, requestInterval, maxAttempts, initialBackoff, maxBackoff,
                System::nanoTime,
                duration -> Thread.sleep(duration.toMillis(), duration.toNanosPart() % 1_000_000),
                retryEvents);
    }

    RetryingRateLimitedTourApiClient(
            TourApiClient delegate,
            Duration requestInterval,
            int maxAttempts,
            Duration initialBackoff,
            Duration maxBackoff,
            LongSupplier nanoTime,
            TourApiRetrySleeper sleeper,
            RetryEventListener retryEvents
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.requestInterval = requireNonNegative(requestInterval, "requestInterval");
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
    public TourApiClientResult<TourApiPage<TourApiAreaBasedListItem>> getAreaBasedListPage(
            int pageNo,
            int numOfRows
    ) {
        return execute(() -> delegate.getAreaBasedListPage(pageNo, numOfRows));
    }

    @Override
    public TourApiClientResult<TourApiAreaBasedListItem> getAreaBasedList(TourApiCollectRequest request) {
        return execute(() -> delegate.getAreaBasedList(request));
    }

    @Override
    public TourApiClientResult<TourApiDetailCommonItem> getDetailCommon(String contentId) {
        return execute(() -> delegate.getDetailCommon(contentId));
    }

    @Override
    public TourApiClientResult<TourApiDetailIntroItem> getDetailIntro(
            String contentId,
            String contentTypeId
    ) {
        return execute(() -> delegate.getDetailIntro(contentId, contentTypeId));
    }

    @Override
    public TourApiClientResult<TourApiDetailImageItem> getDetailImage(String contentId) {
        return execute(() -> delegate.getDetailImage(contentId));
    }

    @Override
    public TourApiClientResult<TourApiCodeItem> getRegionCode(String areaCode, String sigunguCode) {
        return execute(() -> delegate.getRegionCode(areaCode, sigunguCode));
    }

    private synchronized <T> TourApiClientResult<T> execute(Supplier<TourApiClientResult<T>> call) {
        TourApiClientResult<T> result = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            awaitRequestInterval();
            result = Objects.requireNonNull(call.get(), "TourAPI result must not be null");
            if (!retryable(result) || attempt == maxAttempts) {
                if (retryable(result) && attempt == maxAttempts) {
                    retryEvents.exhausted("tourapi", reason(result), attempt);
                }
                return result;
            }
            Duration backoff = backoff(attempt);
            retryEvents.retrying("tourapi", reason(result), attempt + 1, backoff);
            sleep(backoff);
        }
        return result;
    }

    private void awaitRequestInterval() {
        long now = nanoTime.getAsLong();
        if (lastCallStartedAt >= 0) {
            long elapsed = Math.max(0L, now - lastCallStartedAt);
            long remaining = requestInterval.toNanos() - elapsed;
            if (remaining > 0) {
                sleep(Duration.ofNanos(remaining));
                now = nanoTime.getAsLong();
            }
        }
        lastCallStartedAt = now;
    }

    private Duration backoff(int completedAttempts) {
        long multiplier = 1L << Math.min(completedAttempts - 1, 30);
        try {
            Duration calculated = initialBackoff.multipliedBy(multiplier);
            return calculated.compareTo(maxBackoff) <= 0 ? calculated : maxBackoff;
        } catch (ArithmeticException exception) {
            return maxBackoff;
        }
    }

    private void sleep(Duration duration) {
        if (duration.isZero()) {
            return;
        }
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("TourAPI call interrupted", exception);
        }
    }

    private static boolean retryable(TourApiClientResult<?> result) {
        if (result.status() != TourApiClientStatus.FAILURE || result.reason() == null) {
            return false;
        }
        String reason = result.reason().toLowerCase(Locale.ROOT);
        if (reason.startsWith("timeout:") || reason.startsWith("connection:")) {
            return true;
        }
        if (!reason.startsWith("http error: status ")) {
            return false;
        }
        try {
            int status = Integer.parseInt(reason.substring("http error: status ".length()).trim());
            return status == 429 || status >= 500 && status <= 599;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static String reason(TourApiClientResult<?> result) {
        String reason = result.reason().toLowerCase(Locale.ROOT);
        int separator = reason.indexOf(':');
        return separator > 0 ? reason.substring(0, separator).replace(' ', '_') : "failure";
    }

    private static Duration requirePositive(Duration duration, String name) {
        requireNonNegative(duration, name);
        if (duration.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return duration;
    }

    private static Duration requireNonNegative(Duration duration, String name) {
        Objects.requireNonNull(duration, name + " must not be null");
        if (duration.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return duration;
    }
}
