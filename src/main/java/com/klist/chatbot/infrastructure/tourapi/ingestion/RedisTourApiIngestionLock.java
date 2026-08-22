package com.klist.chatbot.infrastructure.tourapi.ingestion;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.dao.DataAccessException;

public class RedisTourApiIngestionLock implements TourApiIngestionLock {

    private static final Logger log = LoggerFactory.getLogger(RedisTourApiIngestionLock.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final String key;
    private final Duration ttl;

    public RedisTourApiIngestionLock(StringRedisTemplate redisTemplate, String key, Duration ttl) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        this.key = key;
        this.ttl = ttl;
    }

    @Override
    public Optional<Lease> tryAcquire() {
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, ttl);
            if (!Boolean.TRUE.equals(acquired)) {
                return Optional.empty();
            }
            return Optional.of(() -> release(token));
        } catch (DataAccessException exception) {
            log.error("TourAPI ingestion distributed lock unavailable. key={}", key);
            return Optional.empty();
        }
    }

    private void release(String token) {
        try {
            redisTemplate.execute(RELEASE_SCRIPT, List.of(key), token);
        } catch (DataAccessException exception) {
            log.error("TourAPI ingestion distributed lock release failed. key={}", key);
        }
    }
}
