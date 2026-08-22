package com.klist.chatbot.infrastructure.search.query;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.klist.chatbot.search.application.TouristSpotSearchCriteria;
import com.klist.chatbot.search.application.TouristSpotSearchEvidence;
import com.klist.chatbot.search.application.TouristSpotSearchGateway;
import com.klist.chatbot.search.application.TouristSpotSearchResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

public class CachingTouristSpotSearchGateway implements TouristSpotSearchGateway {

    private static final Logger log = LoggerFactory.getLogger(CachingTouristSpotSearchGateway.class);

    private final TouristSpotSearchGateway delegate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final Duration ttl;
    private final String keyPrefix;

    public CachingTouristSpotSearchGateway(
            TouristSpotSearchGateway delegate,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            TouristSpotSearchCacheProperties properties
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        Objects.requireNonNull(properties, "properties must not be null");
        if (properties.getTtl() == null || properties.getTtl().isZero()
                || properties.getTtl().isNegative()) {
            throw new IllegalArgumentException("cache ttl must be positive");
        }
        if (properties.getKeyPrefix() == null || properties.getKeyPrefix().isBlank()) {
            throw new IllegalArgumentException("cache keyPrefix must not be blank");
        }
        this.enabled = properties.isEnabled();
        this.ttl = properties.getTtl();
        this.keyPrefix = properties.getKeyPrefix();
    }

    @Override
    public TouristSpotSearchResult search(TouristSpotSearchCriteria criteria) {
        return searchCached(criteria, () -> delegate.search(criteria));
    }

    @Override
    public TouristSpotSearchResult search(
            TouristSpotSearchCriteria criteria,
            Duration timeout
    ) {
        Objects.requireNonNull(timeout, "timeout must not be null");
        return searchCached(criteria, () -> delegate.search(criteria, timeout));
    }

    private TouristSpotSearchResult searchCached(
            TouristSpotSearchCriteria criteria,
            Supplier<TouristSpotSearchResult> search
    ) {
        Objects.requireNonNull(criteria, "criteria must not be null");
        if (!enabled) {
            return search.get();
        }

        String key;
        try {
            key = cacheKey(criteria);
        } catch (JsonProcessingException exception) {
            log.warn("Tourist spot search cache key serialization failed.");
            return search.get();
        }

        TouristSpotSearchResult cached = read(key);
        if (cached != null) {
            return cached;
        }

        TouristSpotSearchResult result = search.get();
        write(key, result);
        return result;
    }

    private TouristSpotSearchResult read(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            CachedResult cached = objectMapper.readValue(json, CachedResult.class);
            return new TouristSpotSearchResult(cached.evidence(), cached.totalHits(), Duration.ZERO);
        } catch (DataAccessException exception) {
            log.warn("Tourist spot search cache read unavailable; falling back to Elasticsearch.");
            return null;
        } catch (JsonProcessingException exception) {
            log.warn("Tourist spot search cache value is invalid; falling back to Elasticsearch.");
            return null;
        }
    }

    private void write(String key, TouristSpotSearchResult result) {
        try {
            String json = objectMapper.writeValueAsString(
                    new CachedResult(result.evidence(), result.totalHits())
            );
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (DataAccessException exception) {
            log.warn("Tourist spot search cache write unavailable; returning Elasticsearch result.");
        } catch (JsonProcessingException exception) {
            log.warn("Tourist spot search result cache serialization failed.");
        }
    }

    private String cacheKey(TouristSpotSearchCriteria criteria) throws JsonProcessingException {
        byte[] serialized = objectMapper.writeValueAsString(criteria)
                .getBytes(StandardCharsets.UTF_8);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(serialized);
            return keyPrefix + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record CachedResult(List<TouristSpotSearchEvidence> evidence, long totalHits) {
    }
}
