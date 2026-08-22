package com.klist.chatbot.chat.application;

import java.util.List;

record ChatFallbackResponse(String answer, List<String> suggestions) {

    ChatFallbackResponse {
        if (answer == null || answer.isBlank()) {
            throw new IllegalArgumentException("answer must not be blank");
        }
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
    }
}
