package com.klist.chatbot.chat.application.answer;

public class ChatLlmResponseParsingException extends RuntimeException {

    public ChatLlmResponseParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
