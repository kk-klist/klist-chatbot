package com.klist.chatbot.infrastructure.search.failure;

public record TouristSpotIndexRetrySummary(
        int attempted,
        int resolved,
        int rescheduled,
        int exhausted
) {
}
