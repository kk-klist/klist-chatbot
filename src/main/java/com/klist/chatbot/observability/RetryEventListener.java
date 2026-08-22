package com.klist.chatbot.observability;

import java.time.Duration;

public interface RetryEventListener {

    RetryEventListener NO_OP = new RetryEventListener() {
        @Override
        public void retrying(String component, String reason, int nextAttempt, Duration backoff) {
        }

        @Override
        public void exhausted(String component, String reason, int attempts) {
        }
    };

    void retrying(String component, String reason, int nextAttempt, Duration backoff);

    void exhausted(String component, String reason, int attempts);
}
