package com.klist.chatbot.infrastructure.search.index;

import com.klist.chatbot.global.error.ChatbotErrorComponent;
import com.klist.chatbot.global.error.ChatbotErrorType;
import com.klist.chatbot.global.error.ChatbotException;
import java.util.Objects;

public class TouristSpotIndexingException extends ChatbotException {

    private final TouristSpotIndexOperation operation;

    public TouristSpotIndexingException(
            TouristSpotIndexOperation operation,
            String message,
            Throwable cause
    ) {
        super(ChatbotErrorComponent.INDEX, ChatbotErrorType.INDEX_FAILURE,
                message, true, cause);
        this.operation = Objects.requireNonNull(operation, "operation must not be null");
    }

    public TouristSpotIndexOperation operation() {
        return operation;
    }
}
