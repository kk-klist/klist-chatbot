package com.klist.chatbot.chat.application.answer;

import java.util.List;
import java.util.Set;

public class ChatAnswerGroundingException extends RuntimeException {

    private final List<Long> unsupportedTouristSpotIds;
    private final ChatGroundingViolation violation;

    public ChatAnswerGroundingException(Set<Long> unsupportedTouristSpotIds) {
        super("Generated answer contains tourist spots outside the search evidence");
        if (unsupportedTouristSpotIds == null || unsupportedTouristSpotIds.isEmpty()) {
            throw new IllegalArgumentException("unsupportedTouristSpotIds must not be empty");
        }
        this.unsupportedTouristSpotIds = List.copyOf(unsupportedTouristSpotIds);
        this.violation = ChatGroundingViolation.UNSUPPORTED_TOURIST_SPOT;
    }

    public ChatAnswerGroundingException(ChatGroundingViolation violation) {
        super("Generated answer contains a factual claim outside the search evidence: " + violation);
        if (violation == null || violation == ChatGroundingViolation.UNSUPPORTED_TOURIST_SPOT) {
            throw new IllegalArgumentException("factual violation must be specified");
        }
        this.unsupportedTouristSpotIds = List.of();
        this.violation = violation;
    }

    public List<Long> unsupportedTouristSpotIds() {
        return unsupportedTouristSpotIds;
    }

    public ChatGroundingViolation violation() {
        return violation;
    }
}
