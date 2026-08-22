package com.klist.chatbot.global.error;

import java.util.Objects;

public abstract class ChatbotException extends RuntimeException {

    private final ChatbotErrorComponent component;
    private final ChatbotErrorType errorType;
    private final boolean retryable;

    protected ChatbotException(
            ChatbotErrorComponent component,
            ChatbotErrorType errorType,
            String message,
            boolean retryable,
            Throwable cause
    ) {
        super(message, cause);
        this.component = Objects.requireNonNull(component, "component must not be null");
        this.errorType = Objects.requireNonNull(errorType, "errorType must not be null");
        this.retryable = retryable;
    }

    public ChatbotErrorComponent component() {
        return component;
    }

    public ChatbotErrorType errorType() {
        return errorType;
    }

    public boolean retryable() {
        return retryable;
    }
}
