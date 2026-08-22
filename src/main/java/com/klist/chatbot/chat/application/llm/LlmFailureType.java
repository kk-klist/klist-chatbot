package com.klist.chatbot.chat.application.llm;

public enum LlmFailureType {
    CONFIGURATION,
    TIMEOUT,
    CONNECTION,
    AUTHENTICATION,
    RATE_LIMIT,
    MODEL,
    HTTP,
    INVALID_RESPONSE
}
