package com.klist.chatbot.chat.application;

import com.klist.chatbot.chat.application.answer.ChatGeneratedAnswer;
import com.klist.chatbot.chat.application.answer.ChatGeneratedAnswerValidator;
import com.klist.chatbot.chat.application.answer.ChatLlmResponseParser;
import com.klist.chatbot.chat.application.llm.LlmClient;
import com.klist.chatbot.chat.application.llm.LlmGenerationRequest;
import com.klist.chatbot.chat.application.llm.LlmGenerationResult;
import com.klist.chatbot.chat.application.prompt.ChatPrompt;
import com.klist.chatbot.chat.application.prompt.ChatConversationMessage;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

public class ChatCompletionOrchestrator {

    private final ChatSearchOrchestrator searchOrchestrator;
    private final LlmClient llmClient;
    private final ChatLlmResponseParser responseParser;
    private final ChatGeneratedAnswerValidator answerValidator;
    private final ChatQuestionDispositionClassifier dispositionClassifier;
    private final LongSupplier nanoTime;

    public ChatCompletionOrchestrator(
            ChatSearchOrchestrator searchOrchestrator,
            LlmClient llmClient,
            ChatLlmResponseParser responseParser,
            ChatGeneratedAnswerValidator answerValidator
    ) {
        this(searchOrchestrator, llmClient, responseParser, answerValidator,
                new ChatQuestionDispositionClassifier(), System::nanoTime);
    }

    ChatCompletionOrchestrator(
            ChatSearchOrchestrator searchOrchestrator,
            LlmClient llmClient,
            ChatLlmResponseParser responseParser,
            ChatGeneratedAnswerValidator answerValidator,
            LongSupplier nanoTime
    ) {
        this(searchOrchestrator, llmClient, responseParser, answerValidator,
                new ChatQuestionDispositionClassifier(), nanoTime);
    }

    ChatCompletionOrchestrator(
            ChatSearchOrchestrator searchOrchestrator,
            LlmClient llmClient,
            ChatLlmResponseParser responseParser,
            ChatGeneratedAnswerValidator answerValidator,
            ChatQuestionDispositionClassifier dispositionClassifier,
            LongSupplier nanoTime
    ) {
        this.searchOrchestrator = Objects.requireNonNull(
                searchOrchestrator,
                "searchOrchestrator must not be null"
        );
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient must not be null");
        this.responseParser = Objects.requireNonNull(
                responseParser,
                "responseParser must not be null"
        );
        this.answerValidator = Objects.requireNonNull(
                answerValidator,
                "answerValidator must not be null"
        );
        this.dispositionClassifier = Objects.requireNonNull(
                dispositionClassifier, "dispositionClassifier must not be null"
        );
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime must not be null");
    }

    public ChatCompletionResult complete(String question, Duration timeout) {
        return complete(question, List.of(), timeout);
    }

    public ChatCompletionResult complete(
            String question,
            List<ChatConversationMessage> context,
            Duration timeout
    ) {
        ChatQuestionDisposition disposition = dispositionClassifier.classify(question);
        if (disposition == ChatQuestionDisposition.UNSUPPORTED) {
            return ChatCompletionResult.unsupported();
        }
        if (disposition == ChatQuestionDisposition.CLARIFICATION_REQUIRED) {
            return ChatCompletionResult.clarificationRequired();
        }
        ChatProcessingDeadline deadline = new ChatProcessingDeadline(timeout, nanoTime);
        ChatSearchResult searchResult = context.isEmpty()
                ? searchOrchestrator.search(question, List.of(), timeout)
                : searchOrchestrator.search(question, context, timeout);
        Duration remainingTimeout = deadline.remaining("search");
        if (searchResult.promptPreparation().optionalPrompt().isEmpty()) {
            return ChatCompletionResult.noEvidence(searchResult);
        }

        ChatPrompt prompt = searchResult.promptPreparation().prompt();
        LlmGenerationResult generationResult = llmClient.generate(
                new LlmGenerationRequest(prompt, remainingTimeout)
        );
        deadline.check("LLM generation");
        ChatGeneratedAnswer parsedAnswer = responseParser.parse(generationResult);
        deadline.check("response parsing");
        ChatGeneratedAnswer validatedAnswer = answerValidator.validate(
                parsedAnswer,
                searchResult.evidenceContext()
        );
        deadline.check("response validation");
        return ChatCompletionResult.completed(searchResult, generationResult, validatedAnswer);
    }
}
