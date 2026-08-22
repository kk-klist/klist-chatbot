package com.klist.chatbot.infrastructure.search.failure;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TouristSpotIndexFailureJpaRepository
        extends JpaRepository<TouristSpotIndexFailure, Long> {

    Optional<TouristSpotIndexFailure> findByTouristSpotIdAndOperationAndTargetIndex(
            Long touristSpotId,
            TouristSpotIndexFailureOperation operation,
            String targetIndex
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select failure
            from TouristSpotIndexFailure failure
            where failure.status = :status
              and failure.nextRetryAt <= :now
            order by failure.nextRetryAt asc, failure.id asc
            """)
    List<TouristSpotIndexFailure> findDueForUpdate(
            @Param("status") TouristSpotIndexFailureStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    long countByOperationAndTargetIndexAndStatusIn(
            TouristSpotIndexFailureOperation operation,
            String targetIndex,
            List<TouristSpotIndexFailureStatus> statuses
    );
}
