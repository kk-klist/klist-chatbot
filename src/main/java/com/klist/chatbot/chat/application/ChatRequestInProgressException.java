package com.klist.chatbot.chat.application;

public class ChatRequestInProgressException extends RuntimeException {

    public ChatRequestInProgressException() {
        super("The same chat request is still being processed");
    }
}
