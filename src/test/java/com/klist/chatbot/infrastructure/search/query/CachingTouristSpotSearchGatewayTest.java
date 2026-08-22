package com.klist.chatbot.infrastructure.search.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klist.chatbot.search.application.TouristSpotSearchCriteria;
import com.klist.chatbot.search.application.TouristSpotSearchEvidence;
import com.klist.chatbot.search.application.TouristSpotSearchGateway;
import com.klist.chatbot.search.application.TouristSpotSearchResult;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class CachingTouristSpotSearchGatewayTest {

    private final TouristSpotSearchGateway delegate = mock(TouristSpotSearchGateway.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private TouristSpotSearchCacheProperties properties;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        properties = new TouristSpotSearchCacheProperties();
        properties.setEnabled(true);
    }

    @Test
    void returnsCachedResultWithoutCallingElasticsearch() throws Exception {
        String cached = objectMapper.writeValueAsString(new CachedFixture(List.of(evidence()), 1));
        when(valueOperations.get(anyString())).thenReturn(cached);
        CachingTouristSpotSearchGateway gateway = gateway();

        TouristSpotSearchResult result = gateway.search(criteria("경복궁"));

        assertThat(result.evidence()).containsExactly(evidence());
        assertThat(result.totalHits()).isEqualTo(1);
        assertThat(result.executionTime()).isZero();
        verifyNoInteractions(delegate);
    }

    @Test
    void cachesElasticsearchResultWithConfiguredTtl() {
        TouristSpotSearchResult expected = result();
        TouristSpotSearchCriteria criteria = criteria("경복궁");
        when(valueOperations.get(anyString())).thenReturn(null);
        when(delegate.search(criteria)).thenReturn(expected);

        assertThat(gateway().search(criteria)).isSameAs(expected);

        verify(valueOperations).set(
                anyString(),
                anyString(),
                eq(Duration.ofMinutes(5))
        );
    }

    @Test
    void fallsBackToElasticsearchWhenRedisReadFails() {
        TouristSpotSearchCriteria criteria = criteria("경복궁");
        TouristSpotSearchResult expected = result();
        when(valueOperations.get(anyString()))
                .thenThrow(new DataAccessResourceFailureException("unavailable"));
        when(delegate.search(criteria)).thenReturn(expected);

        assertThat(gateway().search(criteria)).isSameAs(expected);
    }

    @Test
    void fallsBackWhenCachedJsonIsInvalid() {
        TouristSpotSearchCriteria criteria = criteria("경복궁");
        TouristSpotSearchResult expected = result();
        when(valueOperations.get(anyString())).thenReturn("not-json");
        when(delegate.search(criteria)).thenReturn(expected);

        assertThat(gateway().search(criteria)).isSameAs(expected);
    }

    @Test
    void returnsElasticsearchResultWhenRedisWriteFails() {
        TouristSpotSearchCriteria criteria = criteria("경복궁");
        TouristSpotSearchResult expected = result();
        when(valueOperations.get(anyString())).thenReturn(null);
        when(delegate.search(criteria)).thenReturn(expected);
        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("unavailable"))
                .when(valueOperations)
                .set(anyString(), anyString(), eq(Duration.ofMinutes(5)));

        assertThat(gateway().search(criteria)).isSameAs(expected);
    }

    @Test
    void usesRedisAgainAfterTransientReadFailureRecovers() throws Exception {
        TouristSpotSearchCriteria criteria = criteria("경복궁");
        TouristSpotSearchResult expected = result();
        String cached = objectMapper.writeValueAsString(new CachedFixture(List.of(evidence()), 1));
        when(valueOperations.get(anyString()))
                .thenThrow(new DataAccessResourceFailureException("unavailable"))
                .thenReturn(cached);
        when(delegate.search(criteria)).thenReturn(expected);
        CachingTouristSpotSearchGateway gateway = gateway();

        assertThat(gateway.search(criteria)).isSameAs(expected);
        assertThat(gateway.search(criteria).evidence()).containsExactly(evidence());

        verify(delegate).search(criteria);
        verify(valueOperations, times(2)).get(anyString());
    }

    @Test
    void forwardsRequestTimeoutBudgetOnCacheMiss() {
        TouristSpotSearchCriteria criteria = criteria("경복궁");
        Duration timeout = Duration.ofSeconds(2);
        TouristSpotSearchResult expected = result();
        when(valueOperations.get(anyString())).thenReturn(null);
        when(delegate.search(criteria, timeout)).thenReturn(expected);

        assertThat(gateway().search(criteria, timeout)).isSameAs(expected);

        verify(delegate).search(criteria, timeout);
    }

    @Test
    void normalizedCriteriaProduceSameCacheKey() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(delegate.search(org.mockito.ArgumentMatchers.any())).thenReturn(result());
        CachingTouristSpotSearchGateway gateway = gateway();

        gateway.search(criteria(" 경복궁 "));
        gateway.search(criteria("경복궁"));

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(valueOperations, org.mockito.Mockito.times(2)).get(keys.capture());
        assertThat(keys.getAllValues()).hasSize(2).allMatch(keys.getAllValues().get(0)::equals);
    }

    @Test
    void bypassesRedisWhenCacheIsDisabled() {
        properties.setEnabled(false);
        TouristSpotSearchCriteria criteria = criteria("경복궁");
        when(delegate.search(criteria)).thenReturn(result());

        gateway().search(criteria);

        verify(redisTemplate, never()).opsForValue();
    }

    private CachingTouristSpotSearchGateway gateway() {
        return new CachingTouristSpotSearchGateway(
                delegate, redisTemplate, objectMapper, properties
        );
    }

    private static TouristSpotSearchCriteria criteria(String keyword) {
        return new TouristSpotSearchCriteria(
                keyword, null, null, null, null, null, null, null, null,
                null, null, null, 5, 0.1f
        );
    }

    private static TouristSpotSearchResult result() {
        return new TouristSpotSearchResult(List.of(evidence()), 1, Duration.ofMillis(20));
    }

    private static TouristSpotSearchEvidence evidence() {
        return new TouristSpotSearchEvidence(
                1L, "경복궁", "조선 궁궐", "서울 종로구", 11L, 21L, 12,
                37.57, 126.98, null, null, null, null, null, 8.5f
        );
    }

    private record CachedFixture(List<TouristSpotSearchEvidence> evidence, long totalHits) {
    }
}
