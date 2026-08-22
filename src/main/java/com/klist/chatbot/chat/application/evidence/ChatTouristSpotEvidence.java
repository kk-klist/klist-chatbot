package com.klist.chatbot.chat.application.evidence;

import java.util.Objects;

public record ChatTouristSpotEvidence(
        Long touristSpotId,
        String title,
        String description,
        String address,
        Integer contentTypeId,
        Double latitude,
        Double longitude,
        String imageUrl,
        String phoneNumber,
        String openingHours,
        String admissionFee,
        String reservationUrl,
        float searchScore
) {

    public ChatTouristSpotEvidence {
        Objects.requireNonNull(touristSpotId, "touristSpotId must not be null");
        title = requireText(title, "title must not be blank");
        description = trimToNull(description);
        address = trimToNull(address);
        imageUrl = trimToNull(imageUrl);
        phoneNumber = trimToNull(phoneNumber);
        openingHours = trimToNull(openingHours);
        admissionFee = trimToNull(admissionFee);
        reservationUrl = trimToNull(reservationUrl);
        if (searchScore < 0) {
            throw new IllegalArgumentException("searchScore must not be negative");
        }
    }

    private static String requireText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
