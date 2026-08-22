package com.klist.chatbot.infrastructure.speech.openai;

import com.klist.chatbot.speech.application.SpeechToTextClient;
import com.klist.chatbot.speech.application.SpeechTranscriptionProperties;
import com.klist.chatbot.speech.application.SpeechTranscriptionService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        SpeechTranscriptionProperties.class,
        OpenAiSpeechToTextProperties.class
})
public class SpeechToTextConfiguration {

    @Bean
    SpeechToTextClient speechToTextClient(OpenAiSpeechToTextProperties properties) {
        return new OpenAiSpeechToTextClient(properties);
    }

    @Bean
    SpeechTranscriptionService speechTranscriptionService(
            SpeechToTextClient client,
            SpeechTranscriptionProperties properties
    ) {
        return new SpeechTranscriptionService(client, properties);
    }
}
