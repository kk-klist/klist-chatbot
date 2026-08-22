package com.klist.chatbot.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.klist.chatbot.domain.touristspot.domain.entity.TouristSpot;
import com.klist.chatbot.domain.touristspot.repository.TouristSpotRepository;
import com.klist.chatbot.infrastructure.search.document.TouristSpotSearchDocument;
import com.klist.chatbot.infrastructure.search.index.TouristSpotIndexingGateway;
import com.klist.chatbot.infrastructure.search.sync.TouristSpotIndexConsistencyService;
import com.klist.chatbot.infrastructure.search.sync.TouristSpotIndexConsistencySummary;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TouristSpotIndexConsistencyServiceTest {

    private final TouristSpotRepository repository = mock(TouristSpotRepository.class);
    private final TouristSpotIndexingGateway gateway = mock(TouristSpotIndexingGateway.class);
    private final TouristSpotIndexConsistencyService service =
            new TouristSpotIndexConsistencyService(repository, gateway);

    @Test
    void summarizesMatchedStaleMissingAheadAndFailedDocumentsAcrossPages() {
        TouristSpot matched = source(1L, version(10));
        TouristSpot stale = source(2L, version(11));
        TouristSpot missing = source(3L, version(12));
        TouristSpot ahead = source(4L, version(13));
        TouristSpot failed = source(5L, version(14));
        when(repository.findPageAfterId(0, 3)).thenReturn(List.of(matched, stale, missing));
        when(repository.findPageAfterId(3, 3)).thenReturn(List.of(ahead, failed));
        when(repository.findPageAfterId(5, 3)).thenReturn(List.of());
        when(gateway.findById(1L)).thenReturn(Optional.of(document(1L, version(10))));
        when(gateway.findById(2L)).thenReturn(Optional.of(document(2L, version(9))));
        when(gateway.findById(3L)).thenReturn(Optional.empty());
        when(gateway.findById(4L)).thenReturn(Optional.of(document(4L, version(15))));
        when(gateway.findById(5L)).thenThrow(new RuntimeException("Elasticsearch unavailable"));

        TouristSpotIndexConsistencySummary summary = service.inspectAll(3);

        assertThat(summary.inspectedCount()).isEqualTo(5);
        assertThat(summary.matchedCount()).isEqualTo(1);
        assertThat(summary.staleIds()).containsExactly(2L);
        assertThat(summary.missingIds()).containsExactly(3L);
        assertThat(summary.aheadIds()).containsExactly(4L);
        assertThat(summary.failedIds()).containsExactly(5L);
        assertThat(summary.consistent()).isFalse();
    }

    @Test
    void reportsConsistentWhenEveryVersionMatches() {
        TouristSpot source = source(1L, version(10));
        when(repository.findPageAfterId(0, 10)).thenReturn(List.of(source));
        when(repository.findPageAfterId(1, 10)).thenReturn(List.of());
        when(gateway.findById(1L)).thenReturn(Optional.of(document(1L, version(10))));

        assertThat(service.inspectAll(10).consistent()).isTrue();
    }

    @Test
    void rejectsNonPositivePageSize() {
        assertThatThrownBy(() -> service.inspectAll(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("pageSize must be positive.");
    }

    private static TouristSpot source(Long id, LocalDateTime modifiedAt) {
        TouristSpot source = TouristSpot.builder()
                .tourApiContentId(1000L + id)
                .contentTypeId(12)
                .name("관광지 " + id)
                .sourceModifiedAt(modifiedAt)
                .lastSyncedAt(modifiedAt)
                .build();
        ReflectionTestUtils.setField(source, "id", id);
        return source;
    }

    private static TouristSpotSearchDocument document(Long id, LocalDateTime modifiedAt) {
        return new TouristSpotSearchDocument(
                id, "관광지 " + id, null, null, null, null, null,
                null, null, null, null, null, modifiedAt
        );
    }

    private static LocalDateTime version(int hour) {
        return LocalDateTime.of(2026, 8, 8, hour, 0);
    }
}
