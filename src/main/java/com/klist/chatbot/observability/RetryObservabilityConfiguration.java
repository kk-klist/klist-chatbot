package com.klist.chatbot.observability;

import com.klist.chatbot.chat.application.ChatMetricsRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RetryObservabilityConfiguration {

    @Bean
    RetryEventListener retryEventListener(MeterRegistry meterRegistry) {
        return new MicrometerRetryEventListener(meterRegistry);
    }

    @Bean
    ChatMetricsRecorder chatMetricsRecorder(MeterRegistry meterRegistry) {
        return new MicrometerChatMetricsRecorder(meterRegistry);
    }
}
