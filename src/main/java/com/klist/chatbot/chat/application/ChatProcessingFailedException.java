package com.klist.chatbot.chat.application;

import com.klist.chatbot.global.error.ChatbotErrorComponent;
import com.klist.chatbot.global.error.ChatbotErrorType;
import com.klist.chatbot.global.error.ChatbotException;

public class ChatProcessingFailedException extends ChatbotException {

    public ChatProcessingFailedException(String message, Throwable cause) {
        super(ChatbotErrorComponent.LLM, ChatbotErrorType.INVALID_RESPONSE, message, false, cause);
    }
}
