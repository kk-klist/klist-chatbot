package com.klist.chatbot.chat.application;

import com.klist.chatbot.chat.application.answer.ChatAnswerGroundingException;
import com.klist.chatbot.chat.application.answer.ChatGeneratedAnswer;
import com.klist.chatbot.chat.application.answer.ChatLlmResponseParsingException;
import com.klist.chatbot.chat.application.answer.ChatRecommendation;
import com.klist.chatbot.chat.application.evidence.ChatTouristSpotEvidence;
import com.klist.chatbot.chat.application.llm.LlmClientException;
import com.klist.chatbot.chat.application.llm.LlmFailureType;
import com.klist.chatbot.chat.application.prompt.ChatConversationMessage;
import com.klist.chatbot.chat.presentation.dto.ChatQueryStatus;
import com.klist.chatbot.chat.presentation.dto.ChatSourceResponse;
import com.klist.chatbot.chat.presentation.dto.ChatSourceType;
import com.klist.chatbot.chat.presentation.dto.InternalChatQueryRequest;
import com.klist.chatbot.chat.presentation.dto.InternalChatQueryResponse;
import com.klist.chatbot.global.error.ChatbotException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InternalChatQueryService implements InternalChatQueryUseCase {

    private static final Logger log = LoggerFactory.getLogger(InternalChatQueryService.class);

    private final ChatCompletionOrchestrator completionOrchestrator;
    private final LongSupplier nanoTime;
    private final ChatMetricsRecorder metrics;

    public InternalChatQueryService(ChatCompletionOrchestrator completionOrchestrator) {
        this(completionOrchestrator, System::nanoTime, ChatMetricsRecorder.NO_OP);
    }

    public InternalChatQueryService(
            ChatCompletionOrchestrator completionOrchestrator,
            ChatMetricsRecorder metrics
    ) {
        this(completionOrchestrator, System::nanoTime, metrics);
    }

    InternalChatQueryService(
            ChatCompletionOrchestrator completionOrchestrator,
            LongSupplier nanoTime
    ) {
        this(completionOrchestrator, nanoTime, ChatMetricsRecorder.NO_OP);
    }

    InternalChatQueryService(
            ChatCompletionOrchestrator completionOrchestrator,
            LongSupplier nanoTime,
            ChatMetricsRecorder metrics
    ) {
        this.completionOrchestrator = Objects.requireNonNull(
                completionOrchestrator,
                "completionOrchestrator must not be null"
        );
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    @Override
    public InternalChatQueryResponse query(InternalChatQueryRequest request, String traceId) {
        Objects.requireNonNull(request, "request must not be null");
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        long startedAt = nanoTime.getAsLong();
        try {
            ChatCompletionResult completionResult = complete(request);
            Duration processingTime = elapsed(startedAt, nanoTime.getAsLong());
            InternalChatQueryResponse response = toResponse(
                    completionResult,
                    request,
                    traceId,
                    processingTime.toMillis()
            );
            recordCompleted(completionResult, processingTime);
            return response;
        } catch (RuntimeException exception) {
            recordFailed(
                    failureComponent(exception),
                    failureReason(exception),
                    elapsed(startedAt, nanoTime.getAsLong())
            );
            throw exception;
        }
    }

    private void recordCompleted(ChatCompletionResult result, Duration processingTime) {
        try {
            metrics.completed(result, processingTime);
        } catch (RuntimeException exception) {
            log.warn("Completed chat metrics could not be recorded", exception);
        }
    }

    private void recordFailed(String component, String reason, Duration processingTime) {
        try {
            metrics.failed(component, reason, processingTime);
        } catch (RuntimeException exception) {
            log.warn("Failed chat metrics could not be recorded", exception);
        }
    }

    private static InternalChatQueryResponse toResponse(
            ChatCompletionResult completionResult,
            InternalChatQueryRequest request,
            String traceId,
            long processingTimeMs
    ) {
        if (completionResult.status() == ChatCompletionStatus.NO_EVIDENCE) {
            ChatFallbackResponse fallback = ChatFallbackResponses.noResult(
                    completionResult.searchResult().questionAnalysis()
            );
            return new InternalChatQueryResponse(
                    request.requestId(),
                    fallback.answer(),
                    List.of(),
                    fallback.suggestions(),
                    traceId,
                    ChatQueryStatus.NO_RESULT,
                    processingTimeMs
            );
        }
        if (completionResult.status() == ChatCompletionStatus.UNSUPPORTED) {
            return fallbackResponse(
                    request, traceId, processingTimeMs, ChatQueryStatus.UNSUPPORTED,
                    ChatFallbackResponses.unsupported()
            );
        }
        if (completionResult.status() == ChatCompletionStatus.CLARIFICATION_REQUIRED) {
            return fallbackResponse(
                    request, traceId, processingTimeMs,
                    ChatQueryStatus.CLARIFICATION_REQUIRED,
                    ChatFallbackResponses.clarificationRequired()
            );
        }
        ChatGeneratedAnswer generatedAnswer = completionResult.generatedAnswer();
        return new InternalChatQueryResponse(
                request.requestId(),
                generatedAnswer.answer(),
                toSources(completionResult, generatedAnswer),
                List.of(),
                traceId,
                ChatQueryStatus.COMPLETED,
                processingTimeMs
        );
    }

    private static InternalChatQueryResponse fallbackResponse(
            InternalChatQueryRequest request,
            String traceId,
            long processingTimeMs,
            ChatQueryStatus status,
            ChatFallbackResponse fallback
    ) {
        return new InternalChatQueryResponse(
                request.requestId(), fallback.answer(), List.of(), fallback.suggestions(),
                traceId, status, processingTimeMs
        );
    }

    private ChatCompletionResult complete(InternalChatQueryRequest request) {
        try {
            Duration timeout = Duration.ofMillis(request.effectiveTimeoutMs());
            if (request.context().isEmpty()) {
                return completionOrchestrator.complete(request.message(), timeout);
            }
            List<ChatConversationMessage> context = request.context().stream()
                    .map(message -> new ChatConversationMessage(
                            message.role().name(),
                            message.content()
                    ))
                    .toList();
            return completionOrchestrator.complete(request.message(), context, timeout);
        } catch (LlmClientException exception) {
            throw translateLlmFailure(exception);
        } catch (ChatLlmResponseParsingException | ChatAnswerGroundingException exception) {
            throw new ChatProcessingFailedException("Chat response validation failed", exception);
        }
    }

    private RuntimeException translateLlmFailure(LlmClientException exception) {
        if (exception.failureType() == LlmFailureType.TIMEOUT) {
            return new ChatQueryTimeoutException("LLM request timed out", exception);
        }
        if (exception.failureType() == LlmFailureType.INVALID_RESPONSE
                || (exception.failureType() == LlmFailureType.HTTP && !exception.retryable())) {
            return new ChatProcessingFailedException("LLM response processing failed", exception);
        }
        return new ChatProcessingUnavailableException("LLM processing is unavailable", exception);
    }

    private static List<ChatSourceResponse> toSources(
            ChatCompletionResult completionResult,
            ChatGeneratedAnswer generatedAnswer
    ) {
        Map<Long, ChatTouristSpotEvidence> evidenceById = completionResult.searchResult()
                .evidenceContext()
                .touristSpots()
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        ChatTouristSpotEvidence::touristSpotId,
                        evidence -> evidence
                ));
        return generatedAnswer.recommendations().stream()
                .map(recommendation -> toSource(recommendation, evidenceById))
                .toList();
    }

    private static ChatSourceResponse toSource(
            ChatRecommendation recommendation,
            Map<Long, ChatTouristSpotEvidence> evidenceById
    ) {
        ChatTouristSpotEvidence evidence = evidenceById.get(recommendation.touristSpotId());
        if (evidence == null) {
            throw new IllegalStateException("Validated recommendation evidence is missing");
        }
        return new ChatSourceResponse(
                evidence.touristSpotId(),
                evidence.title(),
                ChatSourceType.TOURIST_SPOT,
                Double.parseDouble(Float.toString(evidence.searchScore())),
                evidence.reservationUrl()
        );
    }

    private static Duration elapsed(long startedAt, long completedAt) {
        return Duration.ofNanos(Math.max(0, completedAt - startedAt));
    }

    private static String failureReason(RuntimeException exception) {
        if (exception instanceof ChatbotException chatbotException) {
            return chatbotException.errorType().metricTag();
        }
        return "unexpected";
    }

    private static String failureComponent(RuntimeException exception) {
        if (exception instanceof ChatbotException chatbotException) {
            return chatbotException.component().metricTag();
        }
        return "chat";
    }
}
