package com.klist.chatbot.chat.application.prompt;

import java.util.Objects;
import java.util.Optional;

public record ChatPromptPreparation(
        ChatPromptPreparationStatus status,
        ChatPrompt prompt
) {

    public ChatPromptPreparation {
        Objects.requireNonNull(status, "status must not be null");
        if (status == ChatPromptPreparationStatus.READY && prompt == null) {
            throw new IllegalArgumentException("READY preparation requires a prompt");
        }
        if (status == ChatPromptPreparationStatus.NO_EVIDENCE && prompt != null) {
            throw new IllegalArgumentException("NO_EVIDENCE preparation must not contain a prompt");
        }
    }

    public static ChatPromptPreparation ready(ChatPrompt prompt) {
        return new ChatPromptPreparation(
                ChatPromptPreparationStatus.READY,
                Objects.requireNonNull(prompt, "prompt must not be null")
        );
    }

    public static ChatPromptPreparation noEvidence() {
        return new ChatPromptPreparation(ChatPromptPreparationStatus.NO_EVIDENCE, null);
    }

    public Optional<ChatPrompt> optionalPrompt() {
        return Optional.ofNullable(prompt);
    }
}
