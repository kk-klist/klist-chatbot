package com.klist.chatbot.infrastructure.search.failure;

import com.klist.chatbot.global.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "tourist_spot_index_failure",
        indexes = {
                @Index(
                        name = "idx_tourist_spot_index_failure_due",
                        columnList = "status,next_retry_at"
                ),
                @Index(
                        name = "idx_tourist_spot_index_failure_target_index",
                        columnList = "target_index"
                )
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_tourist_spot_index_failure_target",
                columnNames = {"tourist_spot_id", "operation", "target_index"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TouristSpotIndexFailure extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tourist_spot_index_failure_id")
    private Long id;

    @Column(name = "tourist_spot_id", nullable = false)
    private Long touristSpotId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 30)
    private TouristSpotIndexFailureOperation operation;

    @Column(name = "target_index", nullable = false, length = 255)
    private String targetIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_type", nullable = false, length = 30)
    private TouristSpotIndexFailureType failureType;

    @Column(name = "failure_message", nullable = false, length = 1000)
    private String failureMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TouristSpotIndexFailureStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "last_failed_at", nullable = false)
    private LocalDateTime lastFailedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    private TouristSpotIndexFailure(
            Long touristSpotId,
            TouristSpotIndexFailureOperation operation,
            String targetIndex
    ) {
        this.touristSpotId = touristSpotId;
        this.operation = operation;
        this.targetIndex = targetIndex;
    }

    public static TouristSpotIndexFailure create(
            Long touristSpotId,
            TouristSpotIndexFailureOperation operation,
            String targetIndex
    ) {
        return new TouristSpotIndexFailure(touristSpotId, operation, targetIndex);
    }

    public void record(
            TouristSpotIndexFailureType type,
            String message,
            LocalDateTime failedAt,
            LocalDateTime nextRetryAt
    ) {
        this.failureType = type;
        this.failureMessage = truncate(message);
        this.lastFailedAt = failedAt;
        this.retryCount = 0;
        this.nextRetryAt = type == TouristSpotIndexFailureType.TRANSIENT ? nextRetryAt : null;
        this.status = type == TouristSpotIndexFailureType.TRANSIENT
                ? TouristSpotIndexFailureStatus.PENDING
                : TouristSpotIndexFailureStatus.EXHAUSTED;
    }

    public void markProcessing() {
        if (status != TouristSpotIndexFailureStatus.PENDING) {
            throw new IllegalStateException("Only a pending index failure can be processed");
        }
        status = TouristSpotIndexFailureStatus.PROCESSING;
    }

    public void reschedule(String message, LocalDateTime failedAt, LocalDateTime retryAt) {
        failureMessage = truncate(message);
        lastFailedAt = failedAt;
        retryCount++;
        nextRetryAt = retryAt;
        status = TouristSpotIndexFailureStatus.PENDING;
    }

    public void exhaust(String message, LocalDateTime failedAt) {
        failureMessage = truncate(message);
        lastFailedAt = failedAt;
        retryCount++;
        nextRetryAt = null;
        status = TouristSpotIndexFailureStatus.EXHAUSTED;
    }

    public void resolve() {
        status = TouristSpotIndexFailureStatus.RESOLVED;
        nextRetryAt = null;
    }

    private static String truncate(String value) {
        String safe = value == null || value.isBlank() ? "Unknown indexing failure" : value;
        return safe.length() <= 1000 ? safe : safe.substring(0, 1000);
    }
}
