package com.klist.chatbot.chat.application.llm;

import com.klist.chatbot.chat.application.prompt.ChatPrompt;
import java.time.Duration;
import java.util.Objects;

public record LlmGenerationRequest(
        ChatPrompt prompt,
        Duration timeout
) {

    private static final Duration MAX_TIMEOUT = Duration.ofMinutes(2);

    public LlmGenerationRequest {
        Objects.requireNonNull(prompt, "prompt must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(MAX_TIMEOUT) > 0) {
            throw new IllegalArgumentException("timeout must be between 1ms and 2m");
        }
    }
}
