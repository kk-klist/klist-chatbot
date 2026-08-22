package com.klist.chatbot.speech.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stt")
public class SpeechTranscriptionProperties {

    private long maxFileSize = 25L * 1024L * 1024L;
    private Duration timeout = Duration.ofSeconds(20);

    public void validate() {
        if (maxFileSize < 1 || maxFileSize > 25L * 1024L * 1024L) {
            throw new IllegalStateException("STT max file size must be between 1 byte and 25MB");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()
                || timeout.compareTo(Duration.ofMinutes(2)) > 0) {
            throw new IllegalStateException("STT timeout must be between 1ms and 2m");
        }
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }
}
