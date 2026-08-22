package com.klist.chatbot.search.fixture;

import com.klist.chatbot.search.application.TouristSpotSearchCriteria;

public record SearchQualityCase(
        String question,
        long expectedTouristSpotId,
        String region,
        int contentTypeId,
        TouristSpotSearchCriteria criteria
) {
}
