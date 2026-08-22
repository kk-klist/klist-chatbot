package com.klist.chatbot.infrastructure.search.query;

import com.klist.chatbot.global.error.ChatbotErrorComponent;
import com.klist.chatbot.global.error.ChatbotErrorType;
import com.klist.chatbot.global.error.ChatbotException;

public class TouristSpotSearchException extends ChatbotException {

    public TouristSpotSearchException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public TouristSpotSearchException(String message, Throwable cause, boolean retryable) {
        super(ChatbotErrorComponent.SEARCH, ChatbotErrorType.SEARCH_FAILURE,
                message, retryable, cause);
    }
}
