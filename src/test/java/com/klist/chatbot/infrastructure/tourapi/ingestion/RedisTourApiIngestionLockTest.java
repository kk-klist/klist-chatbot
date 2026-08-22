package com.klist.chatbot.infrastructure.tourapi.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

class RedisTourApiIngestionLockTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private RedisTourApiIngestionLock lock;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lock = new RedisTourApiIngestionLock(
                redisTemplate,
                "klist:tourapi:ingestion:schedule",
                Duration.ofHours(2)
        );
    }

    @Test
    void acquiresWithTtlAndReleasesOnlyOwnedToken() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(Duration.ofHours(2))))
                .thenReturn(true);

        TourApiIngestionLock.Lease lease = lock.tryAcquire().orElseThrow();
        lease.close();

        verify(redisTemplate).execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("klist:tourapi:ingestion:schedule")),
                anyString()
        );
    }

    @Test
    void returnsEmptyWhenAnotherInstanceOwnsLock() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        assertThat(lock.tryAcquire()).isEmpty();
    }

    @Test
    void failsClosedWhenRedisIsUnavailable() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new DataAccessResourceFailureException("unavailable"));

        assertThat(lock.tryAcquire()).isEmpty();
    }

    @Test
    void acquiresNormallyAfterRedisRecovers() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new DataAccessResourceFailureException("unavailable"))
                .thenReturn(true);

        assertThat(lock.tryAcquire()).isEmpty();
        assertThat(lock.tryAcquire()).isPresent();
    }
}
