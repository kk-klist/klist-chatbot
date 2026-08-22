package com.klist.chatbot.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.klist.chatbot.chat.application.answer.ChatGeneratedAnswer;
import com.klist.chatbot.chat.application.answer.ChatLlmResponseParsingException;
import com.klist.chatbot.chat.application.answer.ChatRecommendation;
import com.klist.chatbot.chat.application.evidence.ChatEvidenceContext;
import com.klist.chatbot.chat.application.evidence.ChatTouristSpotEvidence;
import com.klist.chatbot.chat.presentation.dto.ChatQueryStatus;
import com.klist.chatbot.chat.presentation.dto.ChatSourceType;
import com.klist.chatbot.chat.presentation.dto.InternalChatQueryRequest;
import com.klist.chatbot.chat.application.llm.LlmClientException;
import com.klist.chatbot.chat.application.llm.LlmFailureType;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class InternalChatQueryServiceTest {

    private static final String TRACE_ID = "trace-001";
    private static final UUID REQUEST_ID = UUID.fromString(
            "a22c717d-5a3e-46b5-92fc-f41624b85887"
    );

    private final ChatCompletionOrchestrator completionOrchestrator =
            mock(ChatCompletionOrchestrator.class);

    @Test
    void mapsValidatedAnswerAndRecommendedEvidenceToApiResponse() {
        InternalChatQueryRequest request = request("서울 야경 명소를 추천해줘", 7000);
        ChatTouristSpotEvidence first = evidence(1001L, "서울 전망대", 4.2f, null);
        ChatTouristSpotEvidence second = evidence(
                1002L,
                "한강 공원",
                3.8f,
                "https://example.com/reservations/1002"
        );
        ChatGeneratedAnswer generatedAnswer = new ChatGeneratedAnswer(
                "서울 야경 명소 두 곳을 추천합니다.",
                List.of(
                        new ChatRecommendation(1002L, "한강 야경을 볼 수 있습니다."),
                        new ChatRecommendation(1001L, "도심 전망을 볼 수 있습니다.")
                )
        );
        ChatCompletionResult completionResult = completedResult(
                List.of(first, second),
                generatedAnswer
        );
        when(completionOrchestrator.complete(request.message(), Duration.ofMillis(7000)))
                .thenReturn(completionResult);
        InternalChatQueryService service = new InternalChatQueryService(
                completionOrchestrator,
                nanoTime(1_000_000L, 244_000_000L)
        );

        var response = service.query(request, TRACE_ID);

        assertThat(response.answer()).isEqualTo("서울 야경 명소 두 곳을 추천합니다.");
        assertThat(response.traceId()).isEqualTo(TRACE_ID);
        assertThat(response.status()).isEqualTo(ChatQueryStatus.COMPLETED);
        assertThat(response.processingTimeMs()).isEqualTo(243L);
        assertThat(response.sources()).hasSize(2);
        assertThat(response.sources().get(0)).satisfies(source -> {
            assertThat(source.touristSpotId()).isEqualTo(1002L);
            assertThat(source.title()).isEqualTo("한강 공원");
            assertThat(source.type()).isEqualTo(ChatSourceType.TOURIST_SPOT);
            assertThat(source.score()).isEqualTo(3.8d);
            assertThat(source.referenceUrl()).isEqualTo("https://example.com/reservations/1002");
        });
        assertThat(response.sources().get(1).touristSpotId()).isEqualTo(1001L);
        verify(completionOrchestrator).complete(request.message(), Duration.ofMillis(7000));
    }

    @Test
    void returnsFixedNoResultResponseWithoutSources() {
        InternalChatQueryRequest request = request("없는 관광지", null);
        ChatCompletionResult completionResult = ChatCompletionResult.noEvidence(
                mock(ChatSearchResult.class)
        );
        when(completionOrchestrator.complete(request.message(), Duration.ofMillis(30000)))
                .thenReturn(completionResult);
        InternalChatQueryService service = new InternalChatQueryService(
                completionOrchestrator,
                nanoTime(10_000_000L, 15_000_000L)
        );

        var response = service.query(request, TRACE_ID);

        assertThat(response.answer()).isEqualTo(
                "조건에 맞는 관광지를 찾지 못했습니다. 다른 지역이나 관광 유형으로 질문해 주세요."
        );
        assertThat(response.sources()).isEmpty();
        assertThat(response.suggestions()).hasSize(2);
        assertThat(response.status()).isEqualTo(ChatQueryStatus.NO_RESULT);
        assertThat(response.processingTimeMs()).isEqualTo(5L);
    }

    @ParameterizedTest
    @MethodSource("nonSearchStatuses")
    void returnsNonSearchStatusWithAnswerAndSuggestions(
            ChatCompletionResult completionResult,
            ChatQueryStatus expectedStatus
    ) {
        InternalChatQueryRequest request = request("질문", null);
        when(completionOrchestrator.complete(request.message(), Duration.ofMillis(30000)))
                .thenReturn(completionResult);
        InternalChatQueryService service = new InternalChatQueryService(
                completionOrchestrator, nanoTime(0L, 1_000_000L)
        );

        var response = service.query(request, TRACE_ID);

        assertThat(response.status()).isEqualTo(expectedStatus);
        assertThat(response.answer()).isNotBlank();
        assertThat(response.suggestions()).isNotEmpty().allMatch(value -> !value.isBlank());
        assertThat(response.sources()).isEmpty();
    }

    @Test
    void rejectsInvalidDirectInvocationInput() {
        InternalChatQueryService service = new InternalChatQueryService(completionOrchestrator);

        assertThatThrownBy(() -> service.query(null, TRACE_ID))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("request must not be null");
        assertThatThrownBy(() -> service.query(request("질문", null), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("traceId must not be blank");
    }

    @ParameterizedTest
    @MethodSource("llmFailures")
    void translatesLlmFailuresToBackendContractExceptions(
            LlmFailureType failureType,
            boolean retryable,
            Class<? extends RuntimeException> expectedType
    ) {
        InternalChatQueryRequest request = request("질문", 3000);
        LlmClientException cause = new LlmClientException(
                failureType,
                "provider detail must stay internal",
                null,
                "provider_code",
                retryable,
                null
        );
        when(completionOrchestrator.complete(request.message(), Duration.ofMillis(3000)))
                .thenThrow(cause);
        InternalChatQueryService service = new InternalChatQueryService(completionOrchestrator);

        assertThatThrownBy(() -> service.query(request, TRACE_ID))
                .isInstanceOf(expectedType)
                .hasCause(cause)
                .hasMessageNotContaining("provider detail")
                .hasMessageNotContaining("provider_code");
    }

    @Test
    void translatesStructuredResponseFailureToProcessingFailed() {
        InternalChatQueryRequest request = request("질문", null);
        ChatLlmResponseParsingException cause = new ChatLlmResponseParsingException(
                "raw response detail",
                null
        );
        when(completionOrchestrator.complete(request.message(), Duration.ofMillis(30000)))
                .thenThrow(cause);
        InternalChatQueryService service = new InternalChatQueryService(completionOrchestrator);

        assertThatThrownBy(() -> service.query(request, TRACE_ID))
                .isInstanceOf(ChatProcessingFailedException.class)
                .hasCause(cause)
                .hasMessageNotContaining("raw response detail");
    }

    @Test
    void returnsSuccessfulResponseWhenCompletedMetricsRecordingFails() {
        InternalChatQueryRequest request = request("서울 야경 명소를 추천해줘", 5000);
        ChatTouristSpotEvidence evidence = evidence(1001L, "서울 전망대", 4.2f, null);
        ChatGeneratedAnswer generatedAnswer = new ChatGeneratedAnswer(
                "서울 전망대를 추천합니다.",
                List.of(new ChatRecommendation(1001L, "야경을 볼 수 있습니다."))
        );
        ChatCompletionResult completionResult = completedResult(
                List.of(evidence), generatedAnswer
        );
        ChatMetricsRecorder metrics = mock(ChatMetricsRecorder.class);
        doThrow(new IllegalStateException("metrics backend unavailable"))
                .when(metrics).completed(completionResult, Duration.ofMillis(5));
        when(completionOrchestrator.complete(request.message(), Duration.ofMillis(5000)))
                .thenReturn(completionResult);
        InternalChatQueryService service = new InternalChatQueryService(
                completionOrchestrator,
                nanoTime(10_000_000L, 15_000_000L),
                metrics
        );

        var response = service.query(request, TRACE_ID);

        assertThat(response.status()).isEqualTo(ChatQueryStatus.COMPLETED);
        assertThat(response.answer()).isEqualTo("서울 전망대를 추천합니다.");
        verify(metrics).completed(completionResult, Duration.ofMillis(5));
    }

    @Test
    void preservesProcessingFailureWhenFailedMetricsRecordingAlsoFails() {
        InternalChatQueryRequest request = request("질문", 5000);
        ChatMetricsRecorder metrics = mock(ChatMetricsRecorder.class);
        IllegalStateException processingFailure = new IllegalStateException("processing failed");
        when(completionOrchestrator.complete(request.message(), Duration.ofMillis(5000)))
                .thenThrow(processingFailure);
        doThrow(new IllegalStateException("metrics backend unavailable"))
                .when(metrics).failed("chat", "unexpected", Duration.ofMillis(5));
        InternalChatQueryService service = new InternalChatQueryService(
                completionOrchestrator,
                nanoTime(10_000_000L, 15_000_000L),
                metrics
        );

        assertThatThrownBy(() -> service.query(request, TRACE_ID))
                .isSameAs(processingFailure);
        verify(metrics).failed("chat", "unexpected", Duration.ofMillis(5));
    }

    private static Stream<Arguments> llmFailures() {
        return Stream.of(
                Arguments.of(LlmFailureType.TIMEOUT, true, ChatQueryTimeoutException.class),
                Arguments.of(LlmFailureType.CONNECTION, true, ChatProcessingUnavailableException.class),
                Arguments.of(LlmFailureType.RATE_LIMIT, true, ChatProcessingUnavailableException.class),
                Arguments.of(LlmFailureType.AUTHENTICATION, false,
                        ChatProcessingUnavailableException.class),
                Arguments.of(LlmFailureType.CONFIGURATION, false,
                        ChatProcessingUnavailableException.class),
                Arguments.of(LlmFailureType.MODEL, false, ChatProcessingUnavailableException.class),
                Arguments.of(LlmFailureType.HTTP, true, ChatProcessingUnavailableException.class),
                Arguments.of(LlmFailureType.HTTP, false, ChatProcessingFailedException.class),
                Arguments.of(LlmFailureType.INVALID_RESPONSE, false,
                        ChatProcessingFailedException.class)
        );
    }

    private static Stream<Arguments> nonSearchStatuses() {
        return Stream.of(
                Arguments.of(ChatCompletionResult.unsupported(), ChatQueryStatus.UNSUPPORTED),
                Arguments.of(
                        ChatCompletionResult.clarificationRequired(),
                        ChatQueryStatus.CLARIFICATION_REQUIRED
                )
        );
    }

    private static ChatCompletionResult completedResult(
            List<ChatTouristSpotEvidence> evidence,
            ChatGeneratedAnswer generatedAnswer
    ) {
        ChatEvidenceContext evidenceContext = new ChatEvidenceContext(
                evidence,
                evidence.size(),
                Duration.ofMillis(4)
        );
        ChatSearchResult searchResult = mock(ChatSearchResult.class);
        when(searchResult.evidenceContext()).thenReturn(evidenceContext);
        return ChatCompletionResult.completed(
                searchResult,
                new com.klist.chatbot.chat.application.llm.LlmGenerationResult(
                        "{\"answer\":\"answer\",\"recommendations\":[]}",
                        "test-model",
                        10,
                        5
                ),
                generatedAnswer
        );
    }

    private static ChatTouristSpotEvidence evidence(
            long id,
            String title,
            float score,
            String reservationUrl
    ) {
        return new ChatTouristSpotEvidence(
                id, title, null, null, 12, null, null,
                null, null, null, null, reservationUrl, score
        );
    }

    private static InternalChatQueryRequest request(String message, Integer timeoutMs) {
        return new InternalChatQueryRequest(
                REQUEST_ID, "session-001", "user-001", message, List.of(), timeoutMs
        );
    }

    private static LongSupplier nanoTime(long... values) {
        AtomicInteger index = new AtomicInteger();
        return () -> values[index.getAndIncrement()];
    }
}
