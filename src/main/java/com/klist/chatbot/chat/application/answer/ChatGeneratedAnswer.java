package com.klist.chatbot.chat.application.answer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record ChatGeneratedAnswer(
        String answer,
        List<ChatRecommendation> recommendations
) {

    private static final int MAX_RECOMMENDATIONS = 20;

    public ChatGeneratedAnswer {
        if (answer == null || answer.isBlank()) {
            throw new IllegalArgumentException("answer must not be blank");
        }
        answer = answer.trim();
        if (recommendations == null) {
            throw new IllegalArgumentException("recommendations must not be null");
        }
        recommendations = List.copyOf(recommendations);
        if (recommendations.size() > MAX_RECOMMENDATIONS) {
            throw new IllegalArgumentException("recommendations must not exceed 20 items");
        }
        Set<Long> ids = new HashSet<>();
        for (ChatRecommendation recommendation : recommendations) {
            if (recommendation == null) {
                throw new IllegalArgumentException("recommendations must not contain null");
            }
            if (!ids.add(recommendation.touristSpotId())) {
                throw new IllegalArgumentException("recommendations must not contain duplicate touristSpotId");
            }
        }
    }
}
