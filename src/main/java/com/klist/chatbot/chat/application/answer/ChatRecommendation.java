package com.klist.chatbot.chat.application.answer;

public record ChatRecommendation(
        long touristSpotId,
        String reason
) {

    public ChatRecommendation {
        if (touristSpotId <= 0) {
            throw new IllegalArgumentException("touristSpotId must be positive");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        reason = reason.trim();
    }
}
