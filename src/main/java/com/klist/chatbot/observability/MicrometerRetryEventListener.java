package com.klist.chatbot.observability;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MicrometerRetryEventListener implements RetryEventListener {

    private static final Logger log = LoggerFactory.getLogger(MicrometerRetryEventListener.class);
    private final MeterRegistry meterRegistry;

    public MicrometerRetryEventListener(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
    }

    @Override
    public void retrying(String component, String reason, int nextAttempt, Duration backoff) {
        meterRegistry.counter(
                "chatbot.retry.attempts",
                "component", component,
                "reason", reason
        ).increment();
        log.warn(
                "External operation retry scheduled. component={}, reason={}, nextAttempt={}, backoffMs={}",
                component,
                reason,
                nextAttempt,
                backoff.toMillis()
        );
    }

    @Override
    public void exhausted(String component, String reason, int attempts) {
        meterRegistry.counter(
                "chatbot.retry.exhausted",
                "component", component,
                "reason", reason
        ).increment();
        log.error(
                "External operation retries exhausted. component={}, reason={}, attempts={}",
                component,
                reason,
                attempts
        );
    }
}
