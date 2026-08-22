package com.klist.chatbot.infrastructure.search.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.klist.chatbot.infrastructure.search.document.TouristSpotSearchCategory;
import com.klist.chatbot.infrastructure.search.document.TouristSpotSearchDocument;
import com.klist.chatbot.infrastructure.search.document.TouristSpotSearchRegion;
import com.klist.chatbot.infrastructure.search.index.TouristSpotIndexProperties;
import com.klist.chatbot.search.application.TouristSpotSearchCriteria;
import com.klist.chatbot.search.application.TouristSpotSearchResult;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

class ElasticsearchTouristSpotSearchGatewayTest {

    private final ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
    private final ElasticsearchTouristSpotSearchGateway gateway =
            new ElasticsearchTouristSpotSearchGateway(operations, properties());

    @Test
    @SuppressWarnings("unchecked")
    void buildsWeightedKeywordFiltersDistanceLimitAndMinimumScore() {
        SearchHits<TouristSpotSearchDocument> hits = mock(SearchHits.class);
        when(hits.getSearchHits()).thenReturn(List.of());
        when(hits.getTotalHits()).thenReturn(0L);
        when(hits.getExecutionDuration()).thenReturn(Duration.ofMillis(7));
        when(operations.search(
                org.mockito.ArgumentMatchers.any(NativeQuery.class),
                eq(TouristSpotSearchDocument.class),
                eq(IndexCoordinates.of("tourist-spots"))
        )).thenReturn(hits);

        gateway.search(criteria());

        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(operations).search(
                captor.capture(),
                eq(TouristSpotSearchDocument.class),
                eq(IndexCoordinates.of("tourist-spots"))
        );
        NativeQuery query = captor.getValue();
        String json = query.getQuery().toString();
        assertThat(json)
                .contains("title^5", "address^3", "description^2")
                .contains("cross_fields")
                .contains("region.areaCode", "category.contentTypeId")
                .contains("geo_distance", "coordinates", "5.0km")
                .contains("title.keyword");
        assertThat(query.getPageable().getPageSize()).isEqualTo(7);
        assertThat(query.getMinScore()).isEqualTo(1.5f);
        assertThat(query.getSourceFilter()).isNotNull();
        assertThat(query.getSourceFilter().getExcludes()).containsExactly("sourceModifiedAt");
    }

    @Test
    @SuppressWarnings("unchecked")
    void convertsSearchHitToChatbotEvidence() {
        SearchHits<TouristSpotSearchDocument> hits = mock(SearchHits.class);
        SearchHit<TouristSpotSearchDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(document());
        when(hit.getScore()).thenReturn(4.2f);
        when(hits.getSearchHits()).thenReturn(List.of(hit));
        when(hits.getTotalHits()).thenReturn(1L);
        when(hits.getExecutionDuration()).thenReturn(Duration.ofMillis(3));
        when(operations.search(
                org.mockito.ArgumentMatchers.any(NativeQuery.class),
                eq(TouristSpotSearchDocument.class),
                eq(IndexCoordinates.of("tourist-spots"))
        )).thenReturn(hits);

        TouristSpotSearchResult result = gateway.search(criteria());

        assertThat(result.totalHits()).isEqualTo(1);
        assertThat(result.executionTime()).isEqualTo(Duration.ofMillis(3));
        assertThat(result.evidence()).singleElement().satisfies(evidence -> {
            assertThat(evidence.touristSpotId()).isEqualTo(1001L);
            assertThat(evidence.title()).isEqualTo("경복궁");
            assertThat(evidence.regionId()).isEqualTo(11L);
            assertThat(evidence.contentTypeId()).isEqualTo(12);
            assertThat(evidence.latitude()).isEqualTo(37.5796);
            assertThat(evidence.score()).isEqualTo(4.2f);
        });
    }

    @Test
    void wrapsElasticsearchFailure() {
        RuntimeException cause = new RuntimeException("connection refused");
        when(operations.search(
                org.mockito.ArgumentMatchers.any(NativeQuery.class),
                eq(TouristSpotSearchDocument.class),
                eq(IndexCoordinates.of("tourist-spots"))
        )).thenThrow(cause);

        assertThatThrownBy(() -> gateway.search(criteria()))
                .isInstanceOf(TouristSpotSearchException.class)
                .hasCause(cause)
                .satisfies(exception -> assertThat(
                        ((TouristSpotSearchException) exception).retryable()
                ).isFalse());
    }

    @Test
    void marksTransientElasticsearchFailureAsRetryable() {
        RuntimeException cause = new TransientDataAccessResourceException("unavailable");
        when(operations.search(
                org.mockito.ArgumentMatchers.any(NativeQuery.class),
                eq(TouristSpotSearchDocument.class),
                eq(IndexCoordinates.of("tourist-spots"))
        )).thenThrow(cause);

        assertThatThrownBy(() -> gateway.search(criteria()))
                .isInstanceOfSatisfying(TouristSpotSearchException.class, exception ->
                        assertThat(exception.retryable()).isTrue());
    }

    @Test
    void marksNestedConnectionFailureAsRetryable() {
        RuntimeException cause = new RuntimeException(
                "wrapped connection failure",
                new ConnectException("connection refused")
        );
        when(operations.search(
                org.mockito.ArgumentMatchers.any(NativeQuery.class),
                eq(TouristSpotSearchDocument.class),
                eq(IndexCoordinates.of("tourist-spots"))
        )).thenThrow(cause);

        assertThatThrownBy(() -> gateway.search(criteria()))
                .isInstanceOfSatisfying(TouristSpotSearchException.class, exception ->
                        assertThat(exception.retryable()).isTrue());
    }

    @Test
    void marksNestedTimeoutFailureAsRetryable() {
        RuntimeException cause = new RuntimeException(
                "wrapped timeout failure",
                new SocketTimeoutException("read timed out")
        );
        when(operations.search(
                org.mockito.ArgumentMatchers.any(NativeQuery.class),
                eq(TouristSpotSearchDocument.class),
                eq(IndexCoordinates.of("tourist-spots"))
        )).thenThrow(cause);

        assertThatThrownBy(() -> gateway.search(criteria()))
                .isInstanceOfSatisfying(TouristSpotSearchException.class, exception ->
                        assertThat(exception.retryable()).isTrue());
    }

    private static TouristSpotSearchCriteria criteria() {
        return new TouristSpotSearchCriteria(
                "서울 궁궐", 11L, "1", "1", 12, 21L,
                "A02", "A0201", "A02010100",
                37.5665, 126.9780, 5.0, 7, 1.5f
        );
    }

    private static TouristSpotSearchDocument document() {
        return new TouristSpotSearchDocument(
                1001L, "경복궁", "조선의 궁궐", "서울특별시 종로구",
                new TouristSpotSearchRegion(11L, "1", "1", "11", "110"),
                new TouristSpotSearchCategory(21L, 12, "A02", "A0201", "A02010100"),
                new GeoPoint(37.5796, 126.9770), null, null, null, null, null, null
        );
    }

    private static TouristSpotIndexProperties properties() {
        TouristSpotIndexProperties properties = new TouristSpotIndexProperties();
        properties.setAlias("tourist-spots");
        properties.setVersion("v1");
        properties.setSettingsLocation(new ClassPathResource("elasticsearch/tourist-spots-settings.json"));
        properties.setMappingsLocation(new ClassPathResource("elasticsearch/tourist-spots-mappings.json"));
        return properties;
    }
}
