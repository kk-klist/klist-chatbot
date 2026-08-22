package com.klist.chatbot.infrastructure.llm.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klist.chatbot.chat.application.llm.LlmClient;
import com.klist.chatbot.chat.application.llm.RetryingLlmClient;
import com.klist.chatbot.observability.RetryEventListener;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpenAiLlmProperties.class)
public class OpenAiLlmConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "llm.openai", name = "enabled", havingValue = "true")
    LlmClient openAiLlmClient(
            OpenAiLlmProperties properties,
            RetryEventListener retryEvents
    ) {
        OpenAiRestClientFactory restClientFactory = new OpenAiRestClientFactory(properties);
        LlmClient client = new OpenAiResponsesClient(
                restClientFactory::create,
                new ObjectMapper(),
                properties
        );
        return new RetryingLlmClient(
                client,
                properties.getRetryMaxAttempts(),
                properties.getRetryInitialBackoff(),
                properties.getRetryMaxBackoff(),
                retryEvents
        );
    }
}
