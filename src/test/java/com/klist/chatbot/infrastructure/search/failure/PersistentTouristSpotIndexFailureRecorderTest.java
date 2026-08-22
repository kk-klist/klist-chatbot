package com.klist.chatbot.infrastructure.search.failure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.klist.chatbot.infrastructure.search.index.TouristSpotIndexingException;
import com.klist.chatbot.infrastructure.search.index.TouristSpotIndexOperation;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PersistentTouristSpotIndexFailureRecorderTest {

    private final TouristSpotIndexFailureJpaRepository repository = mock(
            TouristSpotIndexFailureJpaRepository.class
    );
    private final PersistentTouristSpotIndexFailureRecorder recorder =
            new PersistentTouristSpotIndexFailureRecorder(
                    repository,
                    Clock.fixed(Instant.parse("2026-08-08T12:00:00Z"), ZoneOffset.UTC)
            );

    @Test
    void recordsIndexingFailureAsRetryablePendingFailure() {
        when(repository.findByTouristSpotIdAndOperationAndTargetIndex(
                101L, TouristSpotIndexFailureOperation.INCREMENTAL, "tourist-spots"
        )).thenReturn(Optional.empty());

        recorder.record(
                101L,
                TouristSpotIndexFailureOperation.INCREMENTAL,
                "tourist-spots",
                new TouristSpotIndexingException(
                        TouristSpotIndexOperation.SAVE,
                        "Elasticsearch unavailable",
                        new RuntimeException()
                )
        );

        ArgumentCaptor<TouristSpotIndexFailure> captor = ArgumentCaptor.forClass(
                TouristSpotIndexFailure.class
        );
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getFailureType())
                .isEqualTo(TouristSpotIndexFailureType.TRANSIENT);
        assertThat(captor.getValue().getStatus())
                .isEqualTo(TouristSpotIndexFailureStatus.PENDING);
        assertThat(captor.getValue().getNextRetryAt()).isEqualTo(
                LocalDateTime.of(2026, 8, 8, 12, 0, 30)
        );
    }

    @Test
    void recordsMappingFailureAsExhaustedPermanentFailure() {
        when(repository.findByTouristSpotIdAndOperationAndTargetIndex(
                any(), any(), any()
        )).thenReturn(Optional.empty());

        recorder.record(
                101L,
                TouristSpotIndexFailureOperation.FULL_REINDEX,
                "tourist-spots-v2",
                new IllegalArgumentException("invalid source")
        );

        ArgumentCaptor<TouristSpotIndexFailure> captor = ArgumentCaptor.forClass(
                TouristSpotIndexFailure.class
        );
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getFailureType())
                .isEqualTo(TouristSpotIndexFailureType.PERMANENT);
        assertThat(captor.getValue().getStatus())
                .isEqualTo(TouristSpotIndexFailureStatus.EXHAUSTED);
        assertThat(captor.getValue().getNextRetryAt()).isNull();
    }
}
