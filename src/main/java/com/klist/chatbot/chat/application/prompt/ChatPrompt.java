package com.klist.chatbot.chat.application.prompt;

public record ChatPrompt(
        String systemMessage,
        String userMessage
) {

    public ChatPrompt {
        systemMessage = requireText(systemMessage, "systemMessage must not be blank");
        userMessage = requireText(userMessage, "userMessage must not be blank");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
