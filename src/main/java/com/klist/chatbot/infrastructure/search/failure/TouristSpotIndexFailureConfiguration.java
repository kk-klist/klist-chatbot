package com.klist.chatbot.infrastructure.search.failure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(TouristSpotIndexRetryProperties.class)
public class TouristSpotIndexFailureConfiguration {

    @Bean
    TouristSpotIndexFailureRetryScheduler touristSpotIndexFailureRetryScheduler(
            TouristSpotIndexFailureRetryService retryService,
            TouristSpotIndexRetryProperties properties
    ) {
        return new TouristSpotIndexFailureRetryScheduler(retryService, properties);
    }
}
