package com.klist.chatbot.chat.application;

import com.klist.chatbot.global.error.ChatbotErrorComponent;
import com.klist.chatbot.global.error.ChatbotErrorType;
import com.klist.chatbot.global.error.ChatbotException;

public class ChatProcessingUnavailableException extends ChatbotException {

    public ChatProcessingUnavailableException(String message) {
        this(message, null);
    }

    public ChatProcessingUnavailableException(String message, Throwable cause) {
        super(ChatbotErrorComponent.LLM, ChatbotErrorType.UNAVAILABLE, message, true, cause);
    }
}
