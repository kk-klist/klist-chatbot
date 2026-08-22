package com.klist.chatbot.chat.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChatContextMessage(
        @NotNull ChatContextRole role,
        @NotBlank @Size(max = 4000) String content
) {
}
