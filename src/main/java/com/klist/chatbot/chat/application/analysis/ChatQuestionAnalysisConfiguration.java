package com.klist.chatbot.chat.application.analysis;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ChatQuestionAnalysisProperties.class)
public class ChatQuestionAnalysisConfiguration {

    @Bean
    ChatQuestionAnalyzer chatQuestionAnalyzer(ChatQuestionAnalysisProperties properties) {
        return new ChatQuestionAnalyzer(properties);
    }
}
