package com.klist.chatbot.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.klist.chatbot.domain.touristspot.domain.entity.TouristSpot;
import com.klist.chatbot.domain.touristspot.repository.TouristSpotRepository;
import com.klist.chatbot.infrastructure.search.document.TouristSpotSearchDocument;
import com.klist.chatbot.infrastructure.search.index.TouristSpotIndexingException;
import com.klist.chatbot.infrastructure.search.index.TouristSpotIndexingGateway;
import com.klist.chatbot.infrastructure.search.index.TouristSpotIndexOperation;
import com.klist.chatbot.infrastructure.search.index.TouristSpotIndexProperties;
import com.klist.chatbot.infrastructure.search.failure.TouristSpotIndexFailureOperation;
import com.klist.chatbot.infrastructure.search.failure.TouristSpotIndexFailureRecorder;
import com.klist.chatbot.infrastructure.search.mapper.TouristSpotSearchDocumentMapper;
import com.klist.chatbot.infrastructure.search.sync.TouristSpotIndexSyncResult;
import com.klist.chatbot.infrastructure.search.sync.TouristSpotIndexSyncService;
import com.klist.chatbot.infrastructure.search.sync.TouristSpotIndexSyncStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TouristSpotIndexSyncServiceTest {

    private final TouristSpotRepository repository = mock(TouristSpotRepository.class);
    private final TouristSpotIndexingGateway indexingGateway = mock(TouristSpotIndexingGateway.class);
    private final TouristSpotSearchDocumentMapper mapper = new TouristSpotSearchDocumentMapper();
    private final TouristSpotIndexSyncService service =
            new TouristSpotIndexSyncService(repository, mapper, indexingGateway);

    @Test
    void indexesLatestTouristSpotDocument() {
        TouristSpot touristSpot = touristSpot(101L);
        when(repository.findById(101L)).thenReturn(Optional.of(touristSpot));

        TouristSpotIndexSyncResult result = service.synchronize(101L);

        assertThat(result.status()).isEqualTo(TouristSpotIndexSyncStatus.INDEXED);
        verify(indexingGateway).save(org.mockito.ArgumentMatchers.argThat(document ->
                document.touristSpotId().equals(101L) && document.title().equals("경복궁")));
    }

    @Test
    void returnsNotFoundWhenCommittedSourceDoesNotExist() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        TouristSpotIndexSyncResult result = service.synchronize(404L);

        assertThat(result.status()).isEqualTo(TouristSpotIndexSyncStatus.NOT_FOUND);
    }

    @Test
    void isolatesIndexFailureFromCommittedDatabaseChange() {
        TouristSpot touristSpot = touristSpot(101L);
        when(repository.findById(101L)).thenReturn(Optional.of(touristSpot));
        when(indexingGateway.save(org.mockito.ArgumentMatchers.any(TouristSpotSearchDocument.class)))
                .thenThrow(new TouristSpotIndexingException(
                        TouristSpotIndexOperation.SAVE,
                        "Elasticsearch unavailable",
                        new RuntimeException("connection refused")
                ));

        TouristSpotIndexSyncResult result = service.synchronize(101L);

        assertThat(result.status()).isEqualTo(TouristSpotIndexSyncStatus.FAILED);
        assertThat(result.failureMessage()).contains("Elasticsearch unavailable");
    }

    @Test
    void persistsIncrementalIndexFailureForLaterRetry() {
        TouristSpot touristSpot = touristSpot(101L);
        TouristSpotIndexFailureRecorder recorder = mock(TouristSpotIndexFailureRecorder.class);
        TouristSpotIndexProperties properties = new TouristSpotIndexProperties();
        properties.setAlias("tourist-spots");
        TouristSpotIndexSyncService recordingService = new TouristSpotIndexSyncService(
                repository, mapper, indexingGateway, recorder, properties
        );
        TouristSpotIndexingException failure = new TouristSpotIndexingException(
                TouristSpotIndexOperation.SAVE,
                "Elasticsearch unavailable",
                new RuntimeException("connection refused")
        );
        when(repository.findById(101L)).thenReturn(Optional.of(touristSpot));
        when(indexingGateway.save(org.mockito.ArgumentMatchers.any(TouristSpotSearchDocument.class)))
                .thenThrow(failure);

        recordingService.synchronize(101L);

        verify(recorder).record(
                101L,
                TouristSpotIndexFailureOperation.INCREMENTAL,
                "tourist-spots",
                failure
        );
    }

    private static TouristSpot touristSpot(Long id) {
        TouristSpot touristSpot = TouristSpot.builder()
                .tourApiContentId(126480L)
                .contentTypeId(12)
                .name("경복궁")
                .description("조선의 대표 궁궐")
                .address("서울특별시 종로구")
                .sourceModifiedAt(LocalDateTime.of(2026, 8, 1, 0, 0))
                .lastSyncedAt(LocalDateTime.of(2026, 8, 6, 0, 0))
                .build();
        ReflectionTestUtils.setField(touristSpot, "id", id);
        return touristSpot;
    }
}
