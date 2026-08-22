package com.klist.chatbot.chat.application;

public class ChatRequestIdConflictException extends RuntimeException {

    public ChatRequestIdConflictException() {
        super("The chat request ID was already used for different content");
    }
}
