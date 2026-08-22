package com.klist.chatbot.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.klist.chatbot.infrastructure.search.document.TouristSpotSearchDocument;
import com.klist.chatbot.infrastructure.search.index.ElasticsearchTouristSpotIndexingGateway;
import com.klist.chatbot.infrastructure.search.index.TouristSpotIndexOperation;
import com.klist.chatbot.infrastructure.search.index.TouristSpotIndexProperties;
import com.klist.chatbot.infrastructure.search.index.TouristSpotIndexingException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;

class ElasticsearchTouristSpotIndexingGatewayTest {

    private final ElasticsearchOperations operations = org.mockito.Mockito.mock(ElasticsearchOperations.class);
    private ElasticsearchTouristSpotIndexingGateway gateway;

    @BeforeEach
    void setUp() {
        TouristSpotIndexProperties properties = new TouristSpotIndexProperties();
        properties.setSettingsLocation(new ClassPathResource("elasticsearch/tourist-spots-settings.json"));
        properties.setMappingsLocation(new ClassPathResource("elasticsearch/tourist-spots-mappings.json"));
        gateway = new ElasticsearchTouristSpotIndexingGateway(operations, properties);
    }

    @Test
    void savesOneDocumentThroughAlias() {
        TouristSpotSearchDocument document = document(1L, "경복궁");
        when(operations.save(eq(document), any(IndexCoordinates.class))).thenReturn(document);

        assertThat(gateway.save(document)).isSameAs(document);

        verify(operations).save(eq(document), org.mockito.ArgumentMatchers.argThat(
                coordinates -> coordinates.getIndexName().equals("tourist-spots")
        ));
    }

    @Test
    void bulkSavesDocumentsWithTouristSpotIds() {
        List<TouristSpotSearchDocument> documents = List.of(
                document(1L, "경복궁"),
                document(2L, "창덕궁")
        );

        assertThat(gateway.saveAll(documents)).containsExactlyElementsOf(documents);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<IndexQuery>> queryCaptor = ArgumentCaptor.forClass(List.class);
        verify(operations).bulkIndex(queryCaptor.capture(), org.mockito.ArgumentMatchers.<IndexCoordinates>argThat(
                coordinates -> coordinates.getIndexName().equals("tourist-spots")
        ));
        assertThat(queryCaptor.getValue()).extracting(IndexQuery::getId).containsExactly("1", "2");
    }

    @Test
    void bulkSavesDocumentsDirectlyToRequestedVersionedIndex() {
        List<TouristSpotSearchDocument> documents = List.of(document(1L, "경복궁"));

        gateway.saveAll(documents, "tourist-spots-v2");

        verify(operations).bulkIndex(
                any(),
                org.mockito.ArgumentMatchers.<IndexCoordinates>argThat(
                        coordinates -> coordinates.getIndexName().equals("tourist-spots-v2")
                )
        );
    }

    @Test
    void emptyBulkDoesNotCallElasticsearch() {
        assertThat(gateway.saveAll(List.of())).isEmpty();

        verify(operations, never()).bulkIndex(any(), any(IndexCoordinates.class));
    }

    @Test
    void deletesAndChecksExistenceThroughAlias() {
        when(operations.exists(eq("1"), any(IndexCoordinates.class))).thenReturn(true);

        assertThat(gateway.exists(1L)).isTrue();
        gateway.delete(1L);

        verify(operations).delete(eq("1"), any(IndexCoordinates.class));
    }

    @Test
    void findsDocumentByIdFromAlias() {
        TouristSpotSearchDocument document = document(1L, "경복궁");
        when(operations.get(
                eq("1"),
                eq(TouristSpotSearchDocument.class),
                any(IndexCoordinates.class)
        )).thenReturn(document);

        assertThat(gateway.findById(1L)).contains(document);
    }

    @Test
    void returnsEmptyWhenDocumentDoesNotExist() {
        assertThat(gateway.findById(404L)).isEmpty();
    }

    @Test
    void convertsElasticsearchFailureToApplicationException() {
        when(operations.exists(eq("1"), any(IndexCoordinates.class)))
                .thenThrow(new IllegalStateException("secret cluster details"));

        assertThatThrownBy(() -> gateway.exists(1L))
                .isInstanceOfSatisfying(TouristSpotIndexingException.class, exception -> {
                    assertThat(exception.operation()).isEqualTo(TouristSpotIndexOperation.EXISTS);
                    assertThat(exception.getMessage()).doesNotContain("secret cluster details");
                });
    }

    private static TouristSpotSearchDocument document(Long id, String title) {
        return new TouristSpotSearchDocument(
                id,
                title,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
