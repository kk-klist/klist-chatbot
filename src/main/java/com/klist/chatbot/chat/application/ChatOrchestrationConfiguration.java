package com.klist.chatbot.chat.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klist.chatbot.chat.application.answer.ChatGeneratedAnswerValidator;
import com.klist.chatbot.chat.application.answer.ChatLlmResponseParser;
import com.klist.chatbot.chat.application.analysis.ChatQuestionAnalyzer;
import com.klist.chatbot.chat.application.evidence.ChatSearchEvidenceOrganizer;
import com.klist.chatbot.chat.application.llm.LlmClient;
import com.klist.chatbot.chat.application.prompt.ChatPromptFactory;
import com.klist.chatbot.infrastructure.chat.idempotency.ChatIdempotencyProperties;
import com.klist.chatbot.infrastructure.chat.idempotency.IdempotentInternalChatQueryService;
import com.klist.chatbot.search.application.TouristSpotRetriever;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ChatIdempotencyProperties.class)
public class ChatOrchestrationConfiguration {

    @Bean
    ChatSearchEvidenceOrganizer chatSearchEvidenceOrganizer() {
        return new ChatSearchEvidenceOrganizer();
    }

    @Bean
    ChatPromptFactory chatPromptFactory() {
        return new ChatPromptFactory(new ObjectMapper());
    }

    @Bean
    ChatLlmResponseParser chatLlmResponseParser() {
        return new ChatLlmResponseParser(new ObjectMapper());
    }

    @Bean
    ChatGeneratedAnswerValidator chatGeneratedAnswerValidator() {
        return new ChatGeneratedAnswerValidator();
    }

    @Bean
    @ConditionalOnProperty(prefix = "llm.openai", name = "enabled", havingValue = "true")
    ChatCompletionOrchestrator chatCompletionOrchestrator(
            ChatSearchOrchestrator searchOrchestrator,
            LlmClient llmClient,
            ChatLlmResponseParser responseParser,
            ChatGeneratedAnswerValidator answerValidator
    ) {
        return new ChatCompletionOrchestrator(
                searchOrchestrator,
                llmClient,
                responseParser,
                answerValidator
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "llm.openai", name = "enabled", havingValue = "true")
    InternalChatQueryService internalChatQueryService(
            ChatCompletionOrchestrator completionOrchestrator,
            ChatMetricsRecorder metrics
    ) {
        return new InternalChatQueryService(completionOrchestrator, metrics);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "llm.openai", name = "enabled", havingValue = "true")
    InternalChatQueryUseCase idempotentInternalChatQueryService(
            InternalChatQueryService delegate,
            StringRedisTemplate redisTemplate,
            ChatIdempotencyProperties properties
    ) {
        return new IdempotentInternalChatQueryService(
                delegate,
                redisTemplate,
                new ObjectMapper(),
                properties
        );
    }

    @Bean
    ChatSearchOrchestrator chatSearchOrchestrator(
            ChatQuestionAnalyzer questionAnalyzer,
            TouristSpotRetriever touristSpotRetriever,
            ChatSearchEvidenceOrganizer evidenceOrganizer,
            ChatPromptFactory promptFactory
    ) {
        return new ChatSearchOrchestrator(
                questionAnalyzer,
                touristSpotRetriever,
                evidenceOrganizer,
                promptFactory
        );
    }
}
