package com.klist.chatbot.infrastructure.chat.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klist.chatbot.chat.application.ChatProcessingUnavailableException;
import com.klist.chatbot.chat.application.ChatRequestIdConflictException;
import com.klist.chatbot.chat.application.ChatRequestInProgressException;
import com.klist.chatbot.chat.application.InternalChatQueryUseCase;
import com.klist.chatbot.chat.presentation.dto.ChatQueryStatus;
import com.klist.chatbot.chat.presentation.dto.InternalChatQueryRequest;
import com.klist.chatbot.chat.presentation.dto.InternalChatQueryResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class IdempotentInternalChatQueryServiceTest {

    private static final UUID REQUEST_ID = UUID.fromString(
            "a22c717d-5a3e-46b5-92fc-f41624b85887"
    );
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String PREFIX = "test:chat:request:";

    private final InternalChatQueryUseCase delegate = mock(InternalChatQueryUseCase.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final Map<String, String> redis = new ConcurrentHashMap<>();
    private IdempotentInternalChatQueryService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenAnswer(invocation ->
                redis.get(invocation.getArgument(0)));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(invocation -> redis.putIfAbsent(
                        invocation.getArgument(0), invocation.getArgument(1)
                ) == null);
        doAnswer(invocation -> {
            redis.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        when(redisTemplate.delete(anyString())).thenAnswer(invocation ->
                redis.remove(invocation.getArgument(0)) != null);

        ChatIdempotencyProperties properties = new ChatIdempotencyProperties();
        properties.setTtl(TTL);
        properties.setKeyPrefix(PREFIX);
        service = new IdempotentInternalChatQueryService(
                delegate, redisTemplate, new ObjectMapper(), properties
        );
    }

    @Test
    void cachesCompletedResponseAndReusesItWithCurrentTraceId() {
        InternalChatQueryRequest request = request("서울 관광지를 추천해줘");
        when(delegate.query(request, "trace-first")).thenReturn(response("trace-first"));

        InternalChatQueryResponse first = service.query(request, "trace-first");
        InternalChatQueryResponse retried = service.query(request, "trace-retry");

        assertThat(first.traceId()).isEqualTo("trace-first");
        assertThat(retried.answer()).isEqualTo(first.answer());
        assertThat(retried.suggestions()).containsExactly("다른 지역도 찾아줘");
        assertThat(retried.traceId()).isEqualTo("trace-retry");
        verify(delegate, times(1)).query(any(), anyString());
        verify(valueOperations).set(
                org.mockito.ArgumentMatchers.endsWith(":completed"),
                anyString(),
                org.mockito.ArgumentMatchers.eq(TTL)
        );
    }

    @Test
    void rejectsDuplicateWhileSameRequestIsProcessing() {
        InternalChatQueryRequest request = request("서울 관광지를 추천해줘");
        String processingKey = PREFIX + REQUEST_ID + ":processing";
        String fingerprint = fingerprintFromFirstFailedExecution(request, processingKey);
        redis.put(processingKey, fingerprint);

        assertThatThrownBy(() -> service.query(request, "trace-second"))
                .isInstanceOf(ChatRequestInProgressException.class);
        verifyNoInteractions(delegate);
    }

    @Test
    void rejectsSameRequestIdUsedForDifferentContent() {
        InternalChatQueryRequest original = request("서울 관광지를 추천해줘");
        when(delegate.query(original, "trace-first")).thenReturn(response("trace-first"));
        service.query(original, "trace-first");

        assertThatThrownBy(() -> service.query(
                request("부산 관광지를 추천해줘"), "trace-conflict"
        )).isInstanceOf(ChatRequestIdConflictException.class);
        verify(delegate, times(1)).query(any(), anyString());
    }

    @Test
    void failsClosedWhenRedisIsUnavailable() {
        when(valueOperations.get(anyString())).thenThrow(
                new RedisConnectionFailureException("redis unavailable")
        );

        assertThatThrownBy(() -> service.query(request("질문"), "trace-redis"))
                .isInstanceOf(ChatProcessingUnavailableException.class)
                .hasMessageContaining("cache read failed");
        verifyNoInteractions(delegate);
    }

    private String fingerprintFromFirstFailedExecution(
            InternalChatQueryRequest request,
            String processingKey
    ) {
        when(delegate.query(request, "trace-probe")).thenThrow(new IllegalStateException("stop"));
        assertThatThrownBy(() -> service.query(request, "trace-probe"))
                .isInstanceOf(IllegalStateException.class);
        String fingerprint = redis.get(processingKey);
        if (fingerprint != null) {
            return fingerprint;
        }
        String captured = org.mockito.Mockito.mockingDetails(valueOperations)
                .getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("setIfAbsent"))
                .map(invocation -> (String) invocation.getArgument(1))
                .findFirst()
                .orElseThrow();
        org.mockito.Mockito.clearInvocations(delegate);
        return captured;
    }

    private static InternalChatQueryRequest request(String message) {
        return new InternalChatQueryRequest(
                REQUEST_ID, "session-001", "user-001", message, List.of(), 5000
        );
    }

    private static InternalChatQueryResponse response(String traceId) {
        return new InternalChatQueryResponse(
                REQUEST_ID,
                "답변",
                List.of(),
                List.of("다른 지역도 찾아줘"),
                traceId,
                ChatQueryStatus.COMPLETED,
                100
        );
    }
}
