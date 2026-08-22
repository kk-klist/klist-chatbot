package com.klist.chatbot.chat.application;

import com.klist.chatbot.global.error.ChatbotErrorComponent;
import com.klist.chatbot.global.error.ChatbotErrorType;
import com.klist.chatbot.global.error.ChatbotException;

public class ChatQueryTimeoutException extends ChatbotException {

    public ChatQueryTimeoutException(String message) {
        this(message, null);
    }

    public ChatQueryTimeoutException(String message, Throwable cause) {
        super(component(cause), ChatbotErrorType.TIMEOUT, message, true, cause);
    }

    private static ChatbotErrorComponent component(Throwable cause) {
        if (cause instanceof ChatbotException chatbotException) {
            return chatbotException.component();
        }
        return ChatbotErrorComponent.CHAT;
    }
}
