package com.klist.chatbot.chat.application.analysis;

import com.klist.chatbot.search.application.TouristSpotSearchCriteria;

public record ChatQuestionAnalysis(
        String originalQuestion,
        String normalizedKeyword,
        String detectedRegion,
        Integer detectedContentTypeId,
        TouristSpotSearchCriteria searchCriteria
) {
}
