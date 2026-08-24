package com.klist.chatbot.chat.presentation.dto;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record InternalAudioChatQueryResponse(
        UUID requestId,
        String transcription,
        String answer,
        List<ChatSourceResponse> sources,
        List<String> suggestions,
        String traceId,
        ChatQueryStatus status,
        long processingTimeMs
) {

    public InternalAudioChatQueryResponse {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(transcription, "transcription must not be null");
        if (transcription.isBlank()) {
            throw new IllegalArgumentException("transcription must not be blank");
        }
        Objects.requireNonNull(answer, "answer must not be null");
        sources = sources == null ? List.of() : List.copyOf(sources);
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (processingTimeMs < 0) {
            throw new IllegalArgumentException("processingTimeMs must not be negative");
        }
    }

    public static InternalAudioChatQueryResponse from(
            String transcription,
            InternalChatQueryResponse chatResponse
    ) {
        Objects.requireNonNull(chatResponse, "chatResponse must not be null");
        return new InternalAudioChatQueryResponse(
                chatResponse.requestId(),
                transcription,
                chatResponse.answer(),
                chatResponse.sources(),
                chatResponse.suggestions(),
                chatResponse.traceId(),
                chatResponse.status(),
                chatResponse.processingTimeMs()
        );
    }
}
