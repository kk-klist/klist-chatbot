package com.klist.chatbot.infrastructure.search.failure;

import com.klist.chatbot.domain.touristspot.domain.entity.TouristSpot;
import com.klist.chatbot.domain.touristspot.repository.TouristSpotRepository;
import com.klist.chatbot.infrastructure.search.document.TouristSpotSearchDocument;
import com.klist.chatbot.infrastructure.search.index.TouristSpotIndexManager;
import com.klist.chatbot.infrastructure.search.index.TouristSpotIndexingException;
import com.klist.chatbot.infrastructure.search.index.TouristSpotIndexingGateway;
import com.klist.chatbot.infrastructure.search.mapper.TouristSpotSearchDocumentMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TouristSpotIndexFailureRetryService {

    private final TouristSpotIndexFailureJpaRepository failureRepository;
    private final TouristSpotRepository touristSpotRepository;
    private final TouristSpotSearchDocumentMapper mapper;
    private final TouristSpotIndexingGateway indexingGateway;
    private final TouristSpotIndexManager indexManager;
    private final TouristSpotIndexRetryProperties properties;
    private final Clock clock;

    @Autowired
    public TouristSpotIndexFailureRetryService(
            TouristSpotIndexFailureJpaRepository failureRepository,
            TouristSpotRepository touristSpotRepository,
            TouristSpotSearchDocumentMapper mapper,
            TouristSpotIndexingGateway indexingGateway,
            TouristSpotIndexManager indexManager,
            TouristSpotIndexRetryProperties properties
    ) {
        this(failureRepository, touristSpotRepository, mapper, indexingGateway,
                indexManager, properties, Clock.systemUTC());
    }

    TouristSpotIndexFailureRetryService(
            TouristSpotIndexFailureJpaRepository failureRepository,
            TouristSpotRepository touristSpotRepository,
            TouristSpotSearchDocumentMapper mapper,
            TouristSpotIndexingGateway indexingGateway,
            TouristSpotIndexManager indexManager,
            TouristSpotIndexRetryProperties properties,
            Clock clock
    ) {
        this.failureRepository = failureRepository;
        this.touristSpotRepository = touristSpotRepository;
        this.mapper = mapper;
        this.indexingGateway = indexingGateway;
        this.indexManager = indexManager;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public TouristSpotIndexRetrySummary retryDue() {
        properties.validate();
        LocalDateTime now = now();
        List<TouristSpotIndexFailure> failures = failureRepository.findDueForUpdate(
                TouristSpotIndexFailureStatus.PENDING,
                now,
                PageRequest.of(0, properties.getBatchSize())
        );
        int resolved = 0;
        int rescheduled = 0;
        int exhausted = 0;
        for (TouristSpotIndexFailure failure : failures) {
            failure.markProcessing();
            try {
                retry(failure);
                failure.resolve();
                resolved++;
                switchAliasWhenFullReindexRecovered(failure);
            } catch (RuntimeException exception) {
                if (!(exception instanceof TouristSpotIndexingException)
                        || failure.getRetryCount() + 1 >= properties.getMaxAttempts()) {
                    failure.exhaust(describe(exception), now());
                    exhausted++;
                } else {
                    failure.reschedule(
                            describe(exception),
                            now(),
                            now().plus(backoff(failure.getRetryCount() + 1))
                    );
                    rescheduled++;
                }
            }
        }
        return new TouristSpotIndexRetrySummary(
                failures.size(), resolved, rescheduled, exhausted
        );
    }

    private void retry(TouristSpotIndexFailure failure) {
        TouristSpot touristSpot = touristSpotRepository.findById(failure.getTouristSpotId())
                .orElseThrow(() -> new IllegalStateException("Tourist spot source no longer exists"));
        TouristSpotSearchDocument document = mapper.map(touristSpot);
        TouristSpotSearchDocument indexed;
        if (failure.getOperation() == TouristSpotIndexFailureOperation.FULL_REINDEX) {
            indexed = indexingGateway.saveAll(List.of(document), failure.getTargetIndex()).get(0);
        } else {
            indexed = indexingGateway.save(document);
        }
        if (indexed == null || !touristSpot.getSourceModifiedAt().equals(indexed.sourceModifiedAt())) {
            throw new IllegalStateException("Indexed document version does not match PostgreSQL source");
        }
    }

    private void switchAliasWhenFullReindexRecovered(TouristSpotIndexFailure failure) {
        if (failure.getOperation() != TouristSpotIndexFailureOperation.FULL_REINDEX) {
            return;
        }
        long remaining = failureRepository.countByOperationAndTargetIndexAndStatusIn(
                TouristSpotIndexFailureOperation.FULL_REINDEX,
                failure.getTargetIndex(),
                List.of(
                        TouristSpotIndexFailureStatus.PENDING,
                        TouristSpotIndexFailureStatus.PROCESSING,
                        TouristSpotIndexFailureStatus.EXHAUSTED
                )
        );
        if (remaining == 0) {
            indexManager.switchAlias(failure.getTargetIndex());
        }
    }

    private Duration backoff(int attempt) {
        long multiplier = 1L << Math.min(attempt - 1, 20);
        Duration calculated = properties.getInitialBackoff().multipliedBy(multiplier);
        return calculated.compareTo(properties.getMaxBackoff()) > 0
                ? properties.getMaxBackoff()
                : calculated;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static String describe(RuntimeException exception) {
        return exception.getClass().getSimpleName() + ": " + exception.getMessage();
    }
}
