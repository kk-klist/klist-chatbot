package com.klist.chatbot.chat.application;

import java.time.Duration;

public interface ChatMetricsRecorder {

    ChatMetricsRecorder NO_OP = new ChatMetricsRecorder() {
        @Override
        public void completed(ChatCompletionResult result, Duration totalTime) {
        }

        @Override
        public void failed(String component, String reason, Duration totalTime) {
        }
    };

    void completed(ChatCompletionResult result, Duration totalTime);

    void failed(String component, String reason, Duration totalTime);
}
