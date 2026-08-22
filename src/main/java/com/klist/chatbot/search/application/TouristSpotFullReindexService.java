package com.klist.chatbot.search.application;

import com.klist.chatbot.domain.touristspot.domain.entity.TouristSpot;
import com.klist.chatbot.domain.touristspot.repository.TouristSpotRepository;
import com.klist.chatbot.infrastructure.search.document.TouristSpotSearchDocument;
import com.klist.chatbot.infrastructure.search.index.TouristSpotIndexManager;
import com.klist.chatbot.infrastructure.search.index.TouristSpotIndexingGateway;
import com.klist.chatbot.infrastructure.search.mapper.TouristSpotSearchDocumentMapper;
import com.klist.chatbot.infrastructure.search.failure.TouristSpotIndexFailureOperation;
import com.klist.chatbot.infrastructure.search.failure.TouristSpotIndexFailureRecorder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class TouristSpotFullReindexService {

    private final TouristSpotRepository repository;
    private final TouristSpotSearchDocumentMapper mapper;
    private final TouristSpotIndexingGateway indexingGateway;
    private final TouristSpotIndexManager indexManager;
    private final TouristSpotReindexProperties properties;
    private final Clock clock;
    private final TouristSpotIndexFailureRecorder failureRecorder;

    public TouristSpotFullReindexService(
            TouristSpotRepository repository,
            TouristSpotSearchDocumentMapper mapper,
            TouristSpotIndexingGateway indexingGateway,
            TouristSpotIndexManager indexManager,
            TouristSpotReindexProperties properties,
            Clock clock
    ) {
        this(repository, mapper, indexingGateway, indexManager, properties, clock,
                TouristSpotIndexFailureRecorder.NO_OP);
    }

    public TouristSpotFullReindexService(
            TouristSpotRepository repository,
            TouristSpotSearchDocumentMapper mapper,
            TouristSpotIndexingGateway indexingGateway,
            TouristSpotIndexManager indexManager,
            TouristSpotReindexProperties properties,
            Clock clock,
            TouristSpotIndexFailureRecorder failureRecorder
    ) {
        this.repository = repository;
        this.mapper = mapper;
        this.indexingGateway = indexingGateway;
        this.indexManager = indexManager;
        this.properties = properties;
        this.clock = clock;
        this.failureRecorder = failureRecorder;
    }

    public TouristSpotReindexSummary reindexAll() {
        properties.validate();
        Instant startedAt = clock.instant();
        String indexName = indexManager.createNewVersionIndex();
        List<Long> failedIds = new ArrayList<>();
        long successCount = 0;
        long lastSeenId = 0;

        while (true) {
            List<TouristSpot> page = repository.findPageAfterId(lastSeenId, properties.getPageSize());
            if (page.isEmpty()) {
                break;
            }
            lastSeenId = page.get(page.size() - 1).getId();

            List<TouristSpotSearchDocument> documents = new ArrayList<>(page.size());
            for (TouristSpot touristSpot : page) {
                try {
                    documents.add(mapper.map(touristSpot));
                } catch (RuntimeException exception) {
                    failedIds.add(touristSpot.getId());
                    failureRecorder.record(
                            touristSpot.getId(),
                            TouristSpotIndexFailureOperation.FULL_REINDEX,
                            indexName,
                            exception
                    );
                }
            }

            if (!documents.isEmpty()) {
                try {
                    indexingGateway.saveAll(documents, indexName);
                    successCount += documents.size();
                    documents.forEach(document -> failureRecorder.resolve(
                            document.touristSpotId(),
                            TouristSpotIndexFailureOperation.FULL_REINDEX,
                            indexName
                    ));
                } catch (RuntimeException exception) {
                    failedIds.addAll(documents.stream()
                            .map(TouristSpotSearchDocument::touristSpotId)
                            .toList());
                    documents.forEach(document -> failureRecorder.record(
                            document.touristSpotId(),
                            TouristSpotIndexFailureOperation.FULL_REINDEX,
                            indexName,
                            exception
                    ));
                }
            }
        }

        boolean aliasSwitched = false;
        TouristSpotReindexStatus status = TouristSpotReindexStatus.FAILED;
        if (failedIds.isEmpty()) {
            aliasSwitched = indexManager.switchAlias(indexName);
            status = TouristSpotReindexStatus.SUCCESS;
        }

        return new TouristSpotReindexSummary(
                status,
                indexName,
                successCount,
                failedIds.size(),
                failedIds,
                Duration.between(startedAt, clock.instant()),
                aliasSwitched
        );
    }
}
