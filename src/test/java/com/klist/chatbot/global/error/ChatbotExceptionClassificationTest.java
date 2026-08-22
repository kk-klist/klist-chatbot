package com.klist.chatbot.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.klist.chatbot.chat.application.ChatProcessingFailedException;
import com.klist.chatbot.chat.application.ChatProcessingUnavailableException;
import com.klist.chatbot.chat.application.ChatQueryTimeoutException;
import com.klist.chatbot.chat.application.llm.LlmClientException;
import com.klist.chatbot.chat.application.llm.LlmFailureType;
import com.klist.chatbot.infrastructure.search.index.TouristSpotIndexOperation;
import com.klist.chatbot.infrastructure.search.index.TouristSpotIndexingException;
import com.klist.chatbot.infrastructure.search.query.TouristSpotSearchException;
import org.junit.jupiter.api.Test;

class ChatbotExceptionClassificationTest {

    @Test
    void classifiesChatProcessingFailures() {
        assertClassification(
                new ChatProcessingUnavailableException("unavailable"),
                ChatbotErrorComponent.LLM,
                ChatbotErrorType.UNAVAILABLE,
                true
        );
        assertClassification(
                new ChatProcessingFailedException("invalid", new RuntimeException()),
                ChatbotErrorComponent.LLM,
                ChatbotErrorType.INVALID_RESPONSE,
                false
        );
        assertClassification(
                new ChatQueryTimeoutException("deadline"),
                ChatbotErrorComponent.CHAT,
                ChatbotErrorType.TIMEOUT,
                true
        );
    }

    @Test
    void preservesLlmComponentWhenTimeoutIsTranslated() {
        LlmClientException llmTimeout = new LlmClientException(
                LlmFailureType.TIMEOUT, "timeout", null, null, true, null
        );

        assertClassification(
                new ChatQueryTimeoutException("LLM request timed out", llmTimeout),
                ChatbotErrorComponent.LLM,
                ChatbotErrorType.TIMEOUT,
                true
        );
    }

    @Test
    void classifiesInfrastructureFailuresAndRetryability() {
        assertClassification(
                new TouristSpotSearchException("bad query", new RuntimeException(), false),
                ChatbotErrorComponent.SEARCH,
                ChatbotErrorType.SEARCH_FAILURE,
                false
        );
        assertClassification(
                new TouristSpotIndexingException(
                        TouristSpotIndexOperation.SAVE, "index unavailable", new RuntimeException()
                ),
                ChatbotErrorComponent.INDEX,
                ChatbotErrorType.INDEX_FAILURE,
                true
        );
    }

    private static void assertClassification(
            ChatbotException exception,
            ChatbotErrorComponent component,
            ChatbotErrorType errorType,
            boolean retryable
    ) {
        assertThat(exception.component()).isEqualTo(component);
        assertThat(exception.errorType()).isEqualTo(errorType);
        assertThat(exception.retryable()).isEqualTo(retryable);
    }
}
