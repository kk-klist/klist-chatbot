package com.klist.chatbot.chat.application.llm;

import com.klist.chatbot.global.error.ChatbotErrorComponent;
import com.klist.chatbot.global.error.ChatbotErrorType;
import com.klist.chatbot.global.error.ChatbotException;
import java.util.Objects;

public class LlmClientException extends ChatbotException {

    private final LlmFailureType failureType;
    private final Integer httpStatus;
    private final String providerCode;

    public LlmClientException(
            LlmFailureType failureType,
            String message,
            Integer httpStatus,
            String providerCode,
            boolean retryable,
            Throwable cause
    ) {
        super(ChatbotErrorComponent.LLM, errorType(failureType), message, retryable, cause);
        this.failureType = Objects.requireNonNull(failureType, "failureType must not be null");
        this.httpStatus = httpStatus;
        this.providerCode = providerCode;
    }

    public LlmFailureType failureType() {
        return failureType;
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    public String providerCode() {
        return providerCode;
    }

    private static ChatbotErrorType errorType(LlmFailureType failureType) {
        Objects.requireNonNull(failureType, "failureType must not be null");
        return switch (failureType) {
            case TIMEOUT -> ChatbotErrorType.TIMEOUT;
            case INVALID_RESPONSE -> ChatbotErrorType.INVALID_RESPONSE;
            default -> ChatbotErrorType.EXTERNAL_SERVICE_FAILURE;
        };
    }
}
