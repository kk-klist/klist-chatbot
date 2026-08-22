package com.klist.chatbot.chat.presentation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

public record InternalChatQueryRequest(
        @NotNull
        UUID requestId,

        @NotBlank
        @Size(max = 100)
        String sessionId,

        @NotBlank
        @Size(max = 100)
        String userId,

        @NotBlank
        @Size(max = 4000)
        String message,

        @Valid
        @Size(max = 10)
        List<ChatContextMessage> context,

        @Min(100)
        @Max(30000)
        Integer timeoutMs
) {

    private static final int DEFAULT_TIMEOUT_MS = 30000;

    public InternalChatQueryRequest {
        requestId = requestId == null ? UUID.randomUUID() : requestId;
        userId = userId == null && sessionId != null ? sessionId : userId;
        context = context == null ? List.of() : List.copyOf(context);
    }

    public int effectiveTimeoutMs() {
        return timeoutMs == null ? DEFAULT_TIMEOUT_MS : timeoutMs;
    }
}
