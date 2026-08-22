package com.klist.chatbot.chat.application.llm;

import java.time.Duration;
import java.util.Objects;

public record LlmGenerationResult(
        String outputText,
        String model,
        long inputTokens,
        long outputTokens,
        Duration processingTime
) {

    public LlmGenerationResult(
            String outputText,
            String model,
            long inputTokens,
            long outputTokens
    ) {
        this(outputText, model, inputTokens, outputTokens, Duration.ZERO);
    }

    public LlmGenerationResult {
        outputText = requireText(outputText, "outputText must not be blank");
        model = requireText(model, "model must not be blank");
        if (inputTokens < 0 || outputTokens < 0) {
            throw new IllegalArgumentException("token counts must not be negative");
        }
        Objects.requireNonNull(processingTime, "processingTime must not be null");
        if (processingTime.isNegative()) {
            throw new IllegalArgumentException("processingTime must not be negative");
        }
    }

    public long totalTokens() {
        return inputTokens + outputTokens;
    }

    public LlmGenerationResult withProcessingTime(Duration duration) {
        return new LlmGenerationResult(outputText, model, inputTokens, outputTokens, duration);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
