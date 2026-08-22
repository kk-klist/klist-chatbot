package com.klist.chatbot.chat.application;

import com.klist.chatbot.chat.application.analysis.ChatQuestionAnalysis;
import com.klist.chatbot.chat.application.evidence.ChatEvidenceContext;
import com.klist.chatbot.chat.application.prompt.ChatPromptPreparation;
import com.klist.chatbot.search.application.TouristSpotSearchResult;
import java.util.Objects;

public record ChatSearchResult(
        ChatQuestionAnalysis questionAnalysis,
        TouristSpotSearchResult touristSpotSearchResult,
        ChatEvidenceContext evidenceContext,
        ChatPromptPreparation promptPreparation
) {

    public ChatSearchResult {
        Objects.requireNonNull(questionAnalysis, "questionAnalysis must not be null");
        Objects.requireNonNull(touristSpotSearchResult, "touristSpotSearchResult must not be null");
        Objects.requireNonNull(evidenceContext, "evidenceContext must not be null");
        Objects.requireNonNull(promptPreparation, "promptPreparation must not be null");
    }
}
