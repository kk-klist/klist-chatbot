package com.klist.chatbot.search.application;

import java.time.Duration;
import java.util.Objects;

public interface TouristSpotSearchGateway {

    TouristSpotSearchResult search(TouristSpotSearchCriteria criteria);

    default TouristSpotSearchResult search(
            TouristSpotSearchCriteria criteria,
            Duration timeout
    ) {
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        return search(criteria);
    }
}
