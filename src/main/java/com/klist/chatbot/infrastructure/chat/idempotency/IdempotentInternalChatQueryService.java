package com.klist.chatbot.infrastructure.chat.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.klist.chatbot.chat.application.ChatProcessingUnavailableException;
import com.klist.chatbot.chat.application.ChatRequestIdConflictException;
import com.klist.chatbot.chat.application.ChatRequestInProgressException;
import com.klist.chatbot.chat.application.InternalChatQueryUseCase;
import com.klist.chatbot.chat.presentation.dto.InternalChatQueryRequest;
import com.klist.chatbot.chat.presentation.dto.InternalChatQueryResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

public class IdempotentInternalChatQueryService implements InternalChatQueryUseCase {

    private static final Logger log = LoggerFactory.getLogger(
            IdempotentInternalChatQueryService.class
    );

    private final InternalChatQueryUseCase delegate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final Duration ttl;
    private final String keyPrefix;

    public IdempotentInternalChatQueryService(
            InternalChatQueryUseCase delegate,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            ChatIdempotencyProperties properties
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        Objects.requireNonNull(properties, "properties must not be null");
        if (properties.getTtl() == null || properties.getTtl().isZero()
                || properties.getTtl().isNegative()) {
            throw new IllegalArgumentException("idempotency ttl must be positive");
        }
        if (properties.getKeyPrefix() == null || properties.getKeyPrefix().isBlank()) {
            throw new IllegalArgumentException("idempotency keyPrefix must not be blank");
        }
        this.enabled = properties.isEnabled();
        this.ttl = properties.getTtl();
        this.keyPrefix = properties.getKeyPrefix();
    }

    @Override
    public InternalChatQueryResponse query(InternalChatQueryRequest request, String traceId) {
        if (!enabled) {
            return delegate.query(request, traceId);
        }
        String fingerprint = fingerprint(request);
        String baseKey = keyPrefix + request.requestId();
        String completedKey = baseKey + ":completed";
        String processingKey = baseKey + ":processing";

        CachedResponse cached = readCompleted(completedKey);
        if (cached != null) {
            verifyFingerprint(cached.fingerprint(), fingerprint);
            return cached.response().withTraceId(traceId);
        }

        if (!acquire(processingKey, fingerprint)) {
            String activeFingerprint = read(processingKey);
            if (activeFingerprint == null && acquire(processingKey, fingerprint)) {
                return executeAndCache(request, traceId, fingerprint, completedKey, processingKey);
            }
            verifyFingerprint(activeFingerprint, fingerprint);
            throw new ChatRequestInProgressException();
        }
        return executeAndCache(request, traceId, fingerprint, completedKey, processingKey);
    }

    private InternalChatQueryResponse executeAndCache(
            InternalChatQueryRequest request,
            String traceId,
            String fingerprint,
            String completedKey,
            String processingKey
    ) {
        try {
            InternalChatQueryResponse response = delegate.query(request, traceId);
            writeCompleted(completedKey, new CachedResponse(fingerprint, response));
            delete(processingKey);
            return response;
        } catch (RuntimeException exception) {
            try {
                delete(processingKey);
            } catch (ChatProcessingUnavailableException cleanupFailure) {
                log.error("Chat idempotency processing key cleanup failed", cleanupFailure);
            }
            throw exception;
        }
    }

    private CachedResponse readCompleted(String key) {
        String json = read(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, CachedResponse.class);
        } catch (JsonProcessingException exception) {
            throw unavailable("Chat idempotency cache value is invalid", exception);
        }
    }

    private String read(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (DataAccessException exception) {
            throw unavailable("Chat idempotency cache read failed", exception);
        }
    }

    private boolean acquire(String key, String fingerprint) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(
                    key, fingerprint, ttl
            ));
        } catch (DataAccessException exception) {
            throw unavailable("Chat idempotency lock acquisition failed", exception);
        }
    }

    private void writeCompleted(String key, CachedResponse value) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (DataAccessException | JsonProcessingException exception) {
            throw unavailable("Chat idempotency result cache write failed", exception);
        }
    }

    private void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (DataAccessException exception) {
            throw unavailable("Chat idempotency lock cleanup failed", exception);
        }
    }

    private String fingerprint(InternalChatQueryRequest request) {
        FingerprintSource source = new FingerprintSource(
                request.sessionId(), request.userId(), request.message(), request.context()
        );
        try {
            byte[] serialized = objectMapper.writeValueAsBytes(source);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(serialized)
            );
        } catch (JsonProcessingException exception) {
            throw unavailable("Chat request fingerprint serialization failed", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void verifyFingerprint(String stored, String current) {
        if (!Objects.equals(stored, current)) {
            throw new ChatRequestIdConflictException();
        }
    }

    private static ChatProcessingUnavailableException unavailable(
            String message,
            Exception cause
    ) {
        return new ChatProcessingUnavailableException(message, cause);
    }

    private record FingerprintSource(
            String sessionId,
            String userId,
            String message,
            java.util.List<com.klist.chatbot.chat.presentation.dto.ChatContextMessage> context
    ) {
    }

    private record CachedResponse(
            String fingerprint,
            InternalChatQueryResponse response
    ) {
    }
}
