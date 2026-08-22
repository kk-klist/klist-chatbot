package com.klist.chatbot.infrastructure.search.failure;

import com.klist.chatbot.infrastructure.search.index.TouristSpotIndexingException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersistentTouristSpotIndexFailureRecorder
        implements TouristSpotIndexFailureRecorder {

    private static final java.time.Duration INITIAL_RETRY_DELAY = java.time.Duration.ofSeconds(30);

    private final TouristSpotIndexFailureJpaRepository repository;
    private final Clock clock;

    @Autowired
    public PersistentTouristSpotIndexFailureRecorder(
            TouristSpotIndexFailureJpaRepository repository
    ) {
        this(repository, Clock.systemUTC());
    }

    PersistentTouristSpotIndexFailureRecorder(
            TouristSpotIndexFailureJpaRepository repository,
            Clock clock
    ) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            Long touristSpotId,
            TouristSpotIndexFailureOperation operation,
            String targetIndex,
            RuntimeException exception
    ) {
        validate(touristSpotId, operation, targetIndex, exception);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        TouristSpotIndexFailureType type = exception instanceof TouristSpotIndexingException
                ? TouristSpotIndexFailureType.TRANSIENT
                : TouristSpotIndexFailureType.PERMANENT;
        TouristSpotIndexFailure failure = repository
                .findByTouristSpotIdAndOperationAndTargetIndex(
                        touristSpotId, operation, targetIndex
                )
                .orElseGet(() -> TouristSpotIndexFailure.create(
                        touristSpotId, operation, targetIndex
                ));
        failure.record(type, describe(exception), now, now.plus(INITIAL_RETRY_DELAY));
        repository.save(failure);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resolve(
            Long touristSpotId,
            TouristSpotIndexFailureOperation operation,
            String targetIndex
    ) {
        repository.findByTouristSpotIdAndOperationAndTargetIndex(
                touristSpotId, operation, targetIndex
        ).ifPresent(TouristSpotIndexFailure::resolve);
    }

    private static String describe(RuntimeException exception) {
        String message = exception.getMessage();
        return exception.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static void validate(
            Long touristSpotId,
            TouristSpotIndexFailureOperation operation,
            String targetIndex,
            RuntimeException exception
    ) {
        if (touristSpotId == null || touristSpotId <= 0) {
            throw new IllegalArgumentException("touristSpotId must be positive");
        }
        if (operation == null) {
            throw new IllegalArgumentException("operation must not be null");
        }
        if (targetIndex == null || targetIndex.isBlank()) {
            throw new IllegalArgumentException("targetIndex must not be blank");
        }
        if (exception == null) {
            throw new IllegalArgumentException("exception must not be null");
        }
    }
}
