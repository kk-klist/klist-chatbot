package com.klist.chatbot.chat.presentation.dto;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record InternalChatQueryResponse(
        UUID requestId,
        String answer,
        List<ChatSourceResponse> sources,
        List<String> suggestions,
        String traceId,
        ChatQueryStatus status,
        long processingTimeMs
) {

    public InternalChatQueryResponse {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(answer, "answer must not be null");
        if (answer.isBlank()) {
            throw new IllegalArgumentException("answer must not be blank");
        }
        sources = sources == null ? List.of() : List.copyOf(sources);
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (processingTimeMs < 0) {
            throw new IllegalArgumentException("processingTimeMs must not be negative");
        }
    }

    public InternalChatQueryResponse(
            UUID requestId,
            String answer,
            List<ChatSourceResponse> sources,
            String traceId,
            ChatQueryStatus status,
            long processingTimeMs
    ) {
        this(requestId, answer, sources, List.of(), traceId, status, processingTimeMs);
    }

    public InternalChatQueryResponse withTraceId(String currentTraceId) {
        return new InternalChatQueryResponse(
                requestId, answer, sources, suggestions, currentTraceId, status, processingTimeMs
        );
    }
}
