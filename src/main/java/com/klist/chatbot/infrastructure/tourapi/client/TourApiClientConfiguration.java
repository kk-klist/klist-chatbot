package com.klist.chatbot.infrastructure.tourapi.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klist.chatbot.observability.RetryEventListener;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TourApiProperties.class)
public class TourApiClientConfiguration {

    @Bean
    TourApiClient tourApiClient(
            TourApiProperties properties,
            RetryEventListener retryEvents
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getResponseTimeout());

        RestClient restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
        TourApiClient client = new RestTourApiClient(restClient, new ObjectMapper(), properties);
        return new RetryingRateLimitedTourApiClient(
                client,
                properties.getRequestInterval(),
                properties.getRetryMaxAttempts(),
                properties.getRetryInitialBackoff(),
                properties.getRetryMaxBackoff(),
                retryEvents
        );
    }
}
