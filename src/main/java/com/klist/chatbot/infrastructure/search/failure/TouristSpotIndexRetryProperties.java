package com.klist.chatbot.infrastructure.search.failure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "search.tourist-spots.failure-retry")
public class TouristSpotIndexRetryProperties {

    private boolean enabled;
    private int batchSize = 20;
    private int maxAttempts = 5;
    private Duration initialBackoff = Duration.ofSeconds(30);
    private Duration maxBackoff = Duration.ofMinutes(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getInitialBackoff() {
        return initialBackoff;
    }

    public void setInitialBackoff(Duration initialBackoff) {
        this.initialBackoff = initialBackoff;
    }

    public Duration getMaxBackoff() {
        return maxBackoff;
    }

    public void setMaxBackoff(Duration maxBackoff) {
        this.maxBackoff = maxBackoff;
    }

    public void validate() {
        if (batchSize <= 0 || maxAttempts <= 0) {
            throw new IllegalArgumentException("retry batchSize and maxAttempts must be positive");
        }
        if (initialBackoff == null || initialBackoff.isZero() || initialBackoff.isNegative()) {
            throw new IllegalArgumentException("retry initialBackoff must be positive");
        }
        if (maxBackoff == null || maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException("retry maxBackoff must not be less than initialBackoff");
        }
    }
}
