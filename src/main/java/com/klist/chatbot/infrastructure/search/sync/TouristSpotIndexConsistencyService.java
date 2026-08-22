package com.klist.chatbot.infrastructure.search.sync;

import com.klist.chatbot.domain.touristspot.domain.entity.TouristSpot;
import com.klist.chatbot.domain.touristspot.repository.TouristSpotRepository;
import com.klist.chatbot.infrastructure.search.document.TouristSpotSearchDocument;
import com.klist.chatbot.infrastructure.search.index.TouristSpotIndexingGateway;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TouristSpotIndexConsistencyService {

    private final TouristSpotRepository repository;
    private final TouristSpotIndexingGateway indexingGateway;

    public TouristSpotIndexConsistencyService(
            TouristSpotRepository repository,
            TouristSpotIndexingGateway indexingGateway
    ) {
        this.repository = repository;
        this.indexingGateway = indexingGateway;
    }

    @Transactional(readOnly = true)
    public TouristSpotIndexConsistencySummary inspectAll(int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive.");
        }

        long inspected = 0;
        long matched = 0;
        long lastSeenId = 0;
        List<Long> staleIds = new ArrayList<>();
        List<Long> missingIds = new ArrayList<>();
        List<Long> aheadIds = new ArrayList<>();
        List<Long> failedIds = new ArrayList<>();

        while (true) {
            List<TouristSpot> page = repository.findPageAfterId(lastSeenId, pageSize);
            if (page.isEmpty()) {
                break;
            }
            lastSeenId = page.get(page.size() - 1).getId();
            for (TouristSpot source : page) {
                inspected++;
                try {
                    Optional<TouristSpotSearchDocument> indexed = indexingGateway.findById(source.getId());
                    if (indexed.isEmpty()) {
                        missingIds.add(source.getId());
                        continue;
                    }
                    LocalDateTime sourceVersion = source.getSourceModifiedAt();
                    LocalDateTime indexVersion = indexed.orElseThrow().sourceModifiedAt();
                    if (sourceVersion.equals(indexVersion)) {
                        matched++;
                    } else if (indexVersion == null || sourceVersion.isAfter(indexVersion)) {
                        staleIds.add(source.getId());
                    } else {
                        aheadIds.add(source.getId());
                    }
                } catch (RuntimeException exception) {
                    failedIds.add(source.getId());
                }
            }
        }

        return new TouristSpotIndexConsistencySummary(
                inspected, matched, staleIds.size(), missingIds.size(), aheadIds.size(), failedIds.size(),
                staleIds, missingIds, aheadIds, failedIds
        );
    }
}
