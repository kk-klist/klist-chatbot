package com.klist.chatbot.observability;

import com.klist.chatbot.chat.application.ChatCompletionResult;
import com.klist.chatbot.chat.application.ChatMetricsRecorder;
import com.klist.chatbot.chat.application.llm.LlmGenerationResult;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Objects;

public class MicrometerChatMetricsRecorder implements ChatMetricsRecorder {

    private final MeterRegistry registry;

    public MicrometerChatMetricsRecorder(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    @Override
    public void completed(ChatCompletionResult result, Duration totalTime) {
        String status = result.status().name().toLowerCase(java.util.Locale.ROOT);
        registry.timer("chatbot.chat.duration", "status", status).record(totalTime);
        if (result.searchResult() == null) {
            return;
        }
        registry.timer("chatbot.search.duration", "status", status)
                .record(result.searchResult().touristSpotSearchResult().executionTime());
        String searchOutcome = result.status() == com.klist.chatbot.chat.application.ChatCompletionStatus.NO_EVIDENCE
                ? "no_result"
                : "results";
        registry.counter("chatbot.search.requests", "outcome", searchOutcome).increment();
        result.optionalGenerationResult().ifPresent(generation -> {
            registry.counter(
                    "chatbot.llm.requests",
                    "outcome", "success",
                    "reason", "none"
            ).increment();
            recordLlm(generation);
        });
    }

    @Override
    public void failed(String component, String reason, Duration totalTime) {
        registry.timer("chatbot.chat.duration", "status", "failed").record(totalTime);
        registry.counter(
                "chatbot.chat.failures",
                "component", component,
                "reason", reason
        ).increment();
        if ("llm".equals(component)) {
            registry.counter(
                    "chatbot.llm.requests",
                    "outcome", "error",
                    "reason", reason
            ).increment();
        }
    }

    private void recordLlm(LlmGenerationResult result) {
        registry.timer("chatbot.llm.duration", "model", result.model())
                .record(result.processingTime());
        registry.counter("chatbot.llm.tokens", "type", "input")
                .increment(result.inputTokens());
        registry.counter("chatbot.llm.tokens", "type", "output")
                .increment(result.outputTokens());
    }
}
