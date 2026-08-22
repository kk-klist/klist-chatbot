package com.klist.chatbot.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.klist.chatbot.chat.application.answer.ChatAnswerGroundingException;
import com.klist.chatbot.chat.application.answer.ChatGeneratedAnswer;
import com.klist.chatbot.chat.application.answer.ChatGeneratedAnswerValidator;
import com.klist.chatbot.chat.application.answer.ChatLlmResponseParser;
import com.klist.chatbot.chat.application.answer.ChatRecommendation;
import com.klist.chatbot.chat.application.evidence.ChatEvidenceContext;
import com.klist.chatbot.chat.application.evidence.ChatTouristSpotEvidence;
import com.klist.chatbot.chat.application.llm.LlmClient;
import com.klist.chatbot.chat.application.llm.LlmGenerationRequest;
import com.klist.chatbot.chat.application.llm.LlmGenerationResult;
import com.klist.chatbot.chat.application.prompt.ChatPrompt;
import com.klist.chatbot.chat.application.prompt.ChatPromptPreparation;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class ChatCompletionOrchestratorTest {

    private final ChatSearchOrchestrator searchOrchestrator = mock(ChatSearchOrchestrator.class);
    private final LlmClient llmClient = mock(LlmClient.class);
    private final ChatLlmResponseParser responseParser = mock(ChatLlmResponseParser.class);
    private final ChatGeneratedAnswerValidator answerValidator = mock(ChatGeneratedAnswerValidator.class);
    private final ChatCompletionOrchestrator orchestrator = new ChatCompletionOrchestrator(
            searchOrchestrator,
            llmClient,
            responseParser,
            answerValidator,
            () -> 0L
    );

    @Test
    void callsLlmParsesResponseAndValidatesGroundingInOrder() {
        String question = "서울 야경 명소를 추천해줘";
        Duration timeout = Duration.ofSeconds(5);
        ChatPrompt prompt = new ChatPrompt("근거만 사용하세요.", question);
        ChatEvidenceContext evidenceContext = evidenceContext(1001L);
        ChatSearchResult searchResult = searchResult(
                evidenceContext,
                ChatPromptPreparation.ready(prompt)
        );
        LlmGenerationResult generationResult = new LlmGenerationResult(
                "{\"answer\":\"추천합니다.\",\"recommendations\":[]}",
                "test-model",
                100,
                20
        );
        ChatGeneratedAnswer parsedAnswer = new ChatGeneratedAnswer(
                "서울 전망대를 추천합니다.",
                List.of(new ChatRecommendation(1001L, "야경을 볼 수 있습니다."))
        );
        when(searchOrchestrator.search(question, List.of(), timeout)).thenReturn(searchResult);
        when(llmClient.generate(org.mockito.ArgumentMatchers.any())).thenReturn(generationResult);
        when(responseParser.parse(generationResult)).thenReturn(parsedAnswer);
        when(answerValidator.validate(parsedAnswer, evidenceContext)).thenReturn(parsedAnswer);

        ChatCompletionResult result = orchestrator.complete(question, timeout);

        assertThat(result.status()).isEqualTo(ChatCompletionStatus.COMPLETED);
        assertThat(result.searchResult()).isSameAs(searchResult);
        assertThat(result.generationResult()).isSameAs(generationResult);
        assertThat(result.generatedAnswer()).isSameAs(parsedAnswer);
        ArgumentCaptor<LlmGenerationRequest> requestCaptor =
                ArgumentCaptor.forClass(LlmGenerationRequest.class);
        InOrder order = inOrder(searchOrchestrator, llmClient, responseParser, answerValidator);
        order.verify(searchOrchestrator).search(question, List.of(), timeout);
        order.verify(llmClient).generate(requestCaptor.capture());
        order.verify(responseParser).parse(generationResult);
        order.verify(answerValidator).validate(parsedAnswer, evidenceContext);
        assertThat(requestCaptor.getValue().prompt()).isSameAs(prompt);
        assertThat(requestCaptor.getValue().timeout()).isEqualTo(timeout);
    }

    @Test
    void doesNotCallLlmWhenSearchHasNoEvidence() {
        ChatSearchResult searchResult = searchResult(
                new ChatEvidenceContext(List.of(), 0, Duration.ZERO),
                ChatPromptPreparation.noEvidence()
        );
        when(searchOrchestrator.search(
                "없는 관광지", List.of(), Duration.ofSeconds(5)
        )).thenReturn(searchResult);

        ChatCompletionResult result = orchestrator.complete(
                "없는 관광지",
                Duration.ofSeconds(5)
        );

        assertThat(result.status()).isEqualTo(ChatCompletionStatus.NO_EVIDENCE);
        assertThat(result.optionalGenerationResult()).isEmpty();
        assertThat(result.optionalGeneratedAnswer()).isEmpty();
        verifyNoInteractions(llmClient, responseParser, answerValidator);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "삼성전자 주가 알려줘, UNSUPPORTED",
            "추천해줘, CLARIFICATION_REQUIRED"
    })
    void classifiesNonSearchQuestionsWithoutSearchOrLlm(
            String question,
            ChatCompletionStatus expectedStatus
    ) {
        ChatCompletionResult result = orchestrator.complete(question, Duration.ofSeconds(5));

        assertThat(result.status()).isEqualTo(expectedStatus);
        assertThat(result.searchResult()).isNull();
        verifyNoInteractions(searchOrchestrator, llmClient, responseParser, answerValidator);
    }

    @Test
    void propagatesGroundingFailureWithoutCreatingCompletedResult() {
        ChatEvidenceContext evidenceContext = evidenceContext(1001L);
        ChatSearchResult searchResult = searchResult(
                evidenceContext,
                ChatPromptPreparation.ready(new ChatPrompt("시스템", "질문"))
        );
        LlmGenerationResult generationResult = new LlmGenerationResult(
                "{\"answer\":\"답변\",\"recommendations\":[]}",
                "test-model",
                1,
                1
        );
        ChatGeneratedAnswer parsed = new ChatGeneratedAnswer(
                "답변",
                List.of(new ChatRecommendation(9001L, "이유"))
        );
        ChatAnswerGroundingException failure = new ChatAnswerGroundingException(Set.of(9001L));
        when(searchOrchestrator.search(
                "질문", List.of(), Duration.ofSeconds(3)
        )).thenReturn(searchResult);
        when(llmClient.generate(org.mockito.ArgumentMatchers.any())).thenReturn(generationResult);
        when(responseParser.parse(generationResult)).thenReturn(parsed);
        when(answerValidator.validate(parsed, evidenceContext)).thenThrow(failure);

        assertThatThrownBy(() -> orchestrator.complete("질문", Duration.ofSeconds(3)))
                .isSameAs(failure);
    }

    @Test
    void requiresCollaborators() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ChatCompletionOrchestrator(
                        null, llmClient, responseParser, answerValidator
                ))
                .withMessage("searchOrchestrator must not be null");
        assertThatNullPointerException()
                .isThrownBy(() -> new ChatCompletionOrchestrator(
                        searchOrchestrator, null, responseParser, answerValidator
                ))
                .withMessage("llmClient must not be null");
    }

    @Test
    void rejectsCompletionWhenSearchConsumesTimeoutBudget() {
        ChatSearchResult searchResult = searchResult(
                new ChatEvidenceContext(List.of(), 0, Duration.ZERO),
                ChatPromptPreparation.noEvidence()
        );
        when(searchOrchestrator.search(
                "question", List.of(), Duration.ofMillis(5)
        )).thenReturn(searchResult);
        ChatCompletionOrchestrator timedOrchestrator = orchestratorWithTime(
                sequentialNanoTime(0L, 5_000_000L)
        );

        assertThatThrownBy(() -> timedOrchestrator.complete("question", Duration.ofMillis(5)))
                .isInstanceOf(ChatQueryTimeoutException.class)
                .hasMessage("Chat processing timed out after search");
        verifyNoInteractions(llmClient, responseParser, answerValidator);
    }

    @Test
    void passesRemainingTimeoutBudgetToLlm() {
        ChatPrompt prompt = new ChatPrompt("system", "question");
        ChatEvidenceContext evidenceContext = evidenceContext(1001L);
        ChatSearchResult searchResult = searchResult(
                evidenceContext,
                ChatPromptPreparation.ready(prompt)
        );
        LlmGenerationResult generationResult = new LlmGenerationResult(
                "{\"answer\":\"answer\",\"recommendations\":[]}",
                "test-model",
                1,
                1
        );
        ChatGeneratedAnswer parsedAnswer = new ChatGeneratedAnswer("answer", List.of());
        when(searchOrchestrator.search(
                "question", List.of(), Duration.ofMillis(10)
        )).thenReturn(searchResult);
        when(llmClient.generate(org.mockito.ArgumentMatchers.any())).thenReturn(generationResult);
        when(responseParser.parse(generationResult)).thenReturn(parsedAnswer);
        when(answerValidator.validate(parsedAnswer, evidenceContext)).thenReturn(parsedAnswer);
        ChatCompletionOrchestrator timedOrchestrator = orchestratorWithTime(
                sequentialNanoTime(0L, 2_000_000L, 4_000_000L, 4_500_000L, 4_800_000L)
        );

        timedOrchestrator.complete("question", Duration.ofMillis(10));

        ArgumentCaptor<LlmGenerationRequest> requestCaptor =
                ArgumentCaptor.forClass(LlmGenerationRequest.class);
        org.mockito.Mockito.verify(llmClient).generate(requestCaptor.capture());
        assertThat(requestCaptor.getValue().timeout()).isEqualTo(Duration.ofMillis(8));
    }

    @Test
    void stopsBeforeParsingWhenLlmConsumesTimeoutBudget() {
        ChatPrompt prompt = new ChatPrompt("system", "question");
        ChatSearchResult searchResult = searchResult(
                evidenceContext(1001L),
                ChatPromptPreparation.ready(prompt)
        );
        LlmGenerationResult generationResult = new LlmGenerationResult("{}", "test-model", 1, 1);
        when(searchOrchestrator.search(
                "question", List.of(), Duration.ofMillis(5)
        )).thenReturn(searchResult);
        when(llmClient.generate(org.mockito.ArgumentMatchers.any())).thenReturn(generationResult);
        ChatCompletionOrchestrator timedOrchestrator = orchestratorWithTime(
                sequentialNanoTime(0L, 1_000_000L, 5_000_000L)
        );

        assertThatThrownBy(() -> timedOrchestrator.complete("question", Duration.ofMillis(5)))
                .isInstanceOf(ChatQueryTimeoutException.class)
                .hasMessage("Chat processing timed out after LLM generation");
        verifyNoInteractions(responseParser, answerValidator);
    }

    private ChatCompletionOrchestrator orchestratorWithTime(LongSupplier nanoTime) {
        return new ChatCompletionOrchestrator(
                searchOrchestrator,
                llmClient,
                responseParser,
                answerValidator,
                nanoTime
        );
    }

    private static LongSupplier sequentialNanoTime(long... values) {
        AtomicInteger index = new AtomicInteger();
        return () -> values[Math.min(index.getAndIncrement(), values.length - 1)];
    }

    private static ChatSearchResult searchResult(
            ChatEvidenceContext evidenceContext,
            ChatPromptPreparation promptPreparation
    ) {
        ChatSearchResult result = mock(ChatSearchResult.class);
        when(result.evidenceContext()).thenReturn(evidenceContext);
        when(result.promptPreparation()).thenReturn(promptPreparation);
        return result;
    }

    private static ChatEvidenceContext evidenceContext(long id) {
        ChatTouristSpotEvidence evidence = new ChatTouristSpotEvidence(
                id, "서울 전망대", null, null, 12, null, null,
                null, null, null, null, null, 2.0f
        );
        return new ChatEvidenceContext(List.of(evidence), 1, Duration.ofMillis(5));
    }
}
