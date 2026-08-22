package com.klist.chatbot.chat.application.prompt;

import java.util.Objects;

public record ChatConversationMessage(String role, String content) {

    public ChatConversationMessage {
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(content, "content must not be null");
    }
}
