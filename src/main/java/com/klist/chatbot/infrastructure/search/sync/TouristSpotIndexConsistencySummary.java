package com.klist.chatbot.infrastructure.search.sync;

import java.util.List;

public record TouristSpotIndexConsistencySummary(
        long inspectedCount,
        long matchedCount,
        long staleCount,
        long missingCount,
        long aheadCount,
        long failedCount,
        List<Long> staleIds,
        List<Long> missingIds,
        List<Long> aheadIds,
        List<Long> failedIds
) {

    public TouristSpotIndexConsistencySummary {
        staleIds = List.copyOf(staleIds);
        missingIds = List.copyOf(missingIds);
        aheadIds = List.copyOf(aheadIds);
        failedIds = List.copyOf(failedIds);
    }

    public boolean consistent() {
        return staleCount == 0 && missingCount == 0 && aheadCount == 0 && failedCount == 0;
    }
}
