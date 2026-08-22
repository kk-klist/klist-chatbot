package com.klist.chatbot.chat.application.evidence;

import com.klist.chatbot.search.application.TouristSpotSearchEvidence;
import com.klist.chatbot.search.application.TouristSpotSearchResult;
import java.util.Objects;

public class ChatSearchEvidenceOrganizer {

    public ChatEvidenceContext organize(TouristSpotSearchResult searchResult) {
        Objects.requireNonNull(searchResult, "searchResult must not be null");
        return new ChatEvidenceContext(
                searchResult.evidence().stream().map(this::toChatEvidence).toList(),
                searchResult.totalHits(),
                searchResult.executionTime()
        );
    }

    private ChatTouristSpotEvidence toChatEvidence(TouristSpotSearchEvidence evidence) {
        return new ChatTouristSpotEvidence(
                evidence.touristSpotId(),
                evidence.title(),
                evidence.description(),
                evidence.address(),
                evidence.contentTypeId(),
                evidence.latitude(),
                evidence.longitude(),
                evidence.imageUrl(),
                evidence.phoneNumber(),
                evidence.openingHours(),
                evidence.admissionFee(),
                evidence.reservationUrl(),
                evidence.score()
        );
    }
}
