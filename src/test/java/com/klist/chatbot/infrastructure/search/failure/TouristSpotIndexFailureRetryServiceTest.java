package com.klist.chatbot.infrastructure.search.failure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.klist.chatbot.domain.touristspot.domain.entity.TouristSpot;
import com.klist.chatbot.domain.touristspot.repository.TouristSpotRepository;
import com.klist.chatbot.infrastructure.search.document.TouristSpotSearchDocument;
import com.klist.chatbot.infrastructure.search.index.TouristSpotIndexManager;
import com.klist.chatbot.infrastructure.search.index.TouristSpotIndexingException;
import com.klist.chatbot.infrastructure.search.index.TouristSpotIndexingGateway;
import com.klist.chatbot.infrastructure.search.index.TouristSpotIndexOperation;
import com.klist.chatbot.infrastructure.search.mapper.TouristSpotSearchDocumentMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class TouristSpotIndexFailureRetryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-08T12:00:00Z");

    private final TouristSpotIndexFailureJpaRepository failureRepository = mock(
            TouristSpotIndexFailureJpaRepository.class
    );
    private final TouristSpotRepository touristSpotRepository = mock(TouristSpotRepository.class);
    private final TouristSpotSearchDocumentMapper mapper = mock(TouristSpotSearchDocumentMapper.class);
    private final TouristSpotIndexingGateway gateway = mock(TouristSpotIndexingGateway.class);
    private final TouristSpotIndexManager indexManager = mock(TouristSpotIndexManager.class);
    private final TouristSpotIndexRetryProperties properties = new TouristSpotIndexRetryProperties();
    private TouristSpotIndexFailureRetryService service;

    @BeforeEach
    void setUp() {
        properties.setBatchSize(10);
        service = new TouristSpotIndexFailureRetryService(
                failureRepository,
                touristSpotRepository,
                mapper,
                gateway,
                indexManager,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void retriesDueIncrementalFailureAndVerifiesSourceVersion() {
        TouristSpotIndexFailure failure = pending(
                TouristSpotIndexFailureOperation.INCREMENTAL, "tourist-spots"
        );
        TouristSpot source = touristSpot();
        TouristSpotSearchDocument document = document();
        when(failureRepository.findDueForUpdate(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(failure));
        when(touristSpotRepository.findById(101L)).thenReturn(Optional.of(source));
        when(mapper.map(source)).thenReturn(document);
        when(gateway.save(document)).thenReturn(document);

        TouristSpotIndexRetrySummary summary = service.retryDue();

        assertThat(summary).isEqualTo(new TouristSpotIndexRetrySummary(1, 1, 0, 0));
        assertThat(failure.getStatus()).isEqualTo(TouristSpotIndexFailureStatus.RESOLVED);
        verify(indexManager, never()).switchAlias(any());
    }

    @Test
    void reschedulesTransientFailureWithExponentialBackoff() {
        TouristSpotIndexFailure failure = pending(
                TouristSpotIndexFailureOperation.INCREMENTAL, "tourist-spots"
        );
        TouristSpot source = touristSpot();
        TouristSpotSearchDocument document = document();
        when(failureRepository.findDueForUpdate(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(failure));
        when(touristSpotRepository.findById(101L)).thenReturn(Optional.of(source));
        when(mapper.map(source)).thenReturn(document);
        when(gateway.save(document)).thenThrow(new TouristSpotIndexingException(
                TouristSpotIndexOperation.SAVE, "unavailable", new RuntimeException()
        ));

        TouristSpotIndexRetrySummary summary = service.retryDue();

        assertThat(summary.rescheduled()).isOne();
        assertThat(failure.getStatus()).isEqualTo(TouristSpotIndexFailureStatus.PENDING);
        assertThat(failure.getRetryCount()).isOne();
        assertThat(failure.getNextRetryAt()).isEqualTo(
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusSeconds(30)
        );
    }

    @Test
    void switchesAliasAfterLastFullReindexFailureIsRecovered() {
        TouristSpotIndexFailure failure = pending(
                TouristSpotIndexFailureOperation.FULL_REINDEX, "tourist-spots-v2"
        );
        TouristSpot source = touristSpot();
        TouristSpotSearchDocument document = document();
        when(failureRepository.findDueForUpdate(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(failure));
        when(touristSpotRepository.findById(101L)).thenReturn(Optional.of(source));
        when(mapper.map(source)).thenReturn(document);
        when(gateway.saveAll(List.of(document), "tourist-spots-v2"))
                .thenReturn(List.of(document));
        when(failureRepository.countByOperationAndTargetIndexAndStatusIn(
                any(), any(), any()
        )).thenReturn(0L);

        service.retryDue();

        verify(indexManager).switchAlias("tourist-spots-v2");
    }

    private static TouristSpotIndexFailure pending(
            TouristSpotIndexFailureOperation operation,
            String targetIndex
    ) {
        TouristSpotIndexFailure failure = TouristSpotIndexFailure.create(
                101L, operation, targetIndex
        );
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        failure.record(
                TouristSpotIndexFailureType.TRANSIENT,
                "failure",
                now.minusMinutes(1),
                now.minusSeconds(1)
        );
        return failure;
    }

    private static TouristSpot touristSpot() {
        TouristSpot source = TouristSpot.builder()
                .tourApiContentId(126480L)
                .contentTypeId(12)
                .name("경복궁")
                .sourceModifiedAt(LocalDateTime.of(2026, 8, 8, 10, 0))
                .lastSyncedAt(LocalDateTime.of(2026, 8, 8, 10, 0))
                .build();
        ReflectionTestUtils.setField(source, "id", 101L);
        return source;
    }

    private static TouristSpotSearchDocument document() {
        return new TouristSpotSearchDocument(
                101L, "경복궁", null, null, null, null, null,
                null, null, null, null, null,
                LocalDateTime.of(2026, 8, 8, 10, 0)
        );
    }
}
