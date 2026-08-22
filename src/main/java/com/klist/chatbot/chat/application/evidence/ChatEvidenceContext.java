package com.klist.chatbot.chat.application.evidence;

import java.time.Duration;
import java.util.List;

public record ChatEvidenceContext(
        List<ChatTouristSpotEvidence> touristSpots,
        long totalHits,
        Duration searchExecutionTime
) {

    public ChatEvidenceContext {
        touristSpots = touristSpots == null ? List.of() : List.copyOf(touristSpots);
        if (totalHits < 0) {
            throw new IllegalArgumentException("totalHits must not be negative");
        }
        searchExecutionTime = searchExecutionTime == null ? Duration.ZERO : searchExecutionTime;
    }

    public boolean isEmpty() {
        return touristSpots.isEmpty();
    }
}
