package com.klist.chatbot.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
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
import com.klist.chatbot.infrastructure.search.mapper.TouristSpotSearchDocumentMapper;
import com.klist.chatbot.infrastructure.search.failure.TouristSpotIndexFailureOperation;
import com.klist.chatbot.infrastructure.search.failure.TouristSpotIndexFailureRecorder;
import com.klist.chatbot.search.application.TouristSpotFullReindexService;
import com.klist.chatbot.search.application.TouristSpotReindexProperties;
import com.klist.chatbot.search.application.TouristSpotReindexStatus;
import com.klist.chatbot.search.application.TouristSpotReindexSummary;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TouristSpotFullReindexServiceTest {

    private final TouristSpotRepository repository = mock(TouristSpotRepository.class);
    private final TouristSpotSearchDocumentMapper mapper = mock(TouristSpotSearchDocumentMapper.class);
    private final TouristSpotIndexingGateway gateway = mock(TouristSpotIndexingGateway.class);
    private final TouristSpotIndexManager indexManager = mock(TouristSpotIndexManager.class);
    private final TouristSpotIndexFailureRecorder failureRecorder = mock(
            TouristSpotIndexFailureRecorder.class
    );
    private final TouristSpotReindexProperties properties = new TouristSpotReindexProperties();
    private TouristSpotFullReindexService service;

    @BeforeEach
    void setUp() {
        properties.setPageSize(2);
        service = new TouristSpotFullReindexService(
                repository,
                mapper,
                gateway,
                indexManager,
                properties,
                new AdvancingClock(
                        Instant.parse("2026-07-28T10:00:00Z"),
                        Instant.parse("2026-07-28T10:00:03Z")
                ),
                failureRecorder
        );
        when(indexManager.createNewVersionIndex()).thenReturn("tourist-spots-v2");
    }

    @Test
    void indexesPostgresqlPagesInBulkAndSwitchesAliasAfterEveryPageSucceeds() {
        TouristSpot first = entity(1L);
        TouristSpot second = entity(2L);
        TouristSpot third = entity(3L);
        TouristSpotSearchDocument firstDocument = document(1L);
        TouristSpotSearchDocument secondDocument = document(2L);
        TouristSpotSearchDocument thirdDocument = document(3L);
        when(repository.findPageAfterId(0, 2)).thenReturn(List.of(first, second));
        when(repository.findPageAfterId(2, 2)).thenReturn(List.of(third));
        when(repository.findPageAfterId(3, 2)).thenReturn(List.of());
        when(mapper.map(first)).thenReturn(firstDocument);
        when(mapper.map(second)).thenReturn(secondDocument);
        when(mapper.map(third)).thenReturn(thirdDocument);
        when(gateway.saveAll(anyList(), org.mockito.ArgumentMatchers.eq("tourist-spots-v2")))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(indexManager.switchAlias("tourist-spots-v2")).thenReturn(true);

        TouristSpotReindexSummary summary = service.reindexAll();

        assertThat(summary.status()).isEqualTo(TouristSpotReindexStatus.SUCCESS);
        assertThat(summary.successCount()).isEqualTo(3);
        assertThat(summary.failureCount()).isZero();
        assertThat(summary.failedTouristSpotIds()).isEmpty();
        assertThat(summary.executionTime()).isEqualTo(Duration.ofSeconds(3));
        assertThat(summary.aliasSwitched()).isTrue();
        verify(gateway).saveAll(List.of(firstDocument, secondDocument), "tourist-spots-v2");
        verify(gateway).saveAll(List.of(thirdDocument), "tourist-spots-v2");
        verify(indexManager).switchAlias("tourist-spots-v2");
    }

    @Test
    void keepsAliasWhenOneBulkFailsAndReturnsEveryFailedId() {
        TouristSpot first = entity(1L);
        TouristSpot second = entity(2L);
        when(repository.findPageAfterId(0, 2)).thenReturn(List.of(first, second));
        when(repository.findPageAfterId(2, 2)).thenReturn(List.of());
        when(mapper.map(first)).thenReturn(document(1L));
        when(mapper.map(second)).thenReturn(document(2L));
        when(gateway.saveAll(anyList(), org.mockito.ArgumentMatchers.eq("tourist-spots-v2")))
                .thenThrow(mock(TouristSpotIndexingException.class));

        TouristSpotReindexSummary summary = service.reindexAll();

        assertThat(summary.status()).isEqualTo(TouristSpotReindexStatus.FAILED);
        assertThat(summary.successCount()).isZero();
        assertThat(summary.failureCount()).isEqualTo(2);
        assertThat(summary.failedTouristSpotIds()).containsExactly(1L, 2L);
        assertThat(summary.aliasSwitched()).isFalse();
        verify(indexManager, never()).switchAlias("tourist-spots-v2");
        verify(failureRecorder).record(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(TouristSpotIndexFailureOperation.FULL_REINDEX),
                org.mockito.ArgumentMatchers.eq("tourist-spots-v2"),
                org.mockito.ArgumentMatchers.any(RuntimeException.class)
        );
        verify(failureRecorder).record(
                org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.eq(TouristSpotIndexFailureOperation.FULL_REINDEX),
                org.mockito.ArgumentMatchers.eq("tourist-spots-v2"),
                org.mockito.ArgumentMatchers.any(RuntimeException.class)
        );
    }

    @Test
    void continuesOtherDocumentsWhenMappingFailsButDoesNotSwitchAlias() {
        TouristSpot invalid = entity(1L);
        TouristSpot valid = entity(2L);
        TouristSpotSearchDocument validDocument = document(2L);
        when(repository.findPageAfterId(0, 2)).thenReturn(List.of(invalid, valid));
        when(repository.findPageAfterId(2, 2)).thenReturn(List.of());
        when(mapper.map(invalid)).thenThrow(new IllegalArgumentException("invalid document"));
        when(mapper.map(valid)).thenReturn(validDocument);
        when(gateway.saveAll(List.of(validDocument), "tourist-spots-v2"))
                .thenReturn(List.of(validDocument));

        TouristSpotReindexSummary summary = service.reindexAll();

        assertThat(summary.successCount()).isOne();
        assertThat(summary.failureCount()).isOne();
        assertThat(summary.failedTouristSpotIds()).containsExactly(1L);
        verify(indexManager, never()).switchAlias("tourist-spots-v2");
        verify(failureRecorder).record(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(TouristSpotIndexFailureOperation.FULL_REINDEX),
                org.mockito.ArgumentMatchers.eq("tourist-spots-v2"),
                org.mockito.ArgumentMatchers.any(IllegalArgumentException.class)
        );
    }

    private static TouristSpot entity(long id) {
        TouristSpot entity = TouristSpot.builder()
                .tourApiContentId(1000L + id)
                .contentTypeId(12)
                .name("관광지 " + id)
                .sourceModifiedAt(java.time.LocalDateTime.of(2026, 7, 28, 10, 0))
                .lastSyncedAt(java.time.LocalDateTime.of(2026, 7, 28, 10, 0))
                .build();
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private static TouristSpotSearchDocument document(long id) {
        return new TouristSpotSearchDocument(
                id, "관광지 " + id, null, null, null, null, null,
                null, null, null, null, null, null
        );
    }

    private static final class AdvancingClock extends Clock {

        private final List<Instant> instants;
        private int index;

        private AdvancingClock(Instant... instants) {
            this.instants = List.of(instants);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instants.get(index++);
        }
    }
}
