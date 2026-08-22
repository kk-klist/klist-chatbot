package com.klist.chatbot.infrastructure.search.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klist.chatbot.infrastructure.search.query.ElasticsearchTouristSpotSearchGateway;
import com.klist.chatbot.infrastructure.search.query.CachingTouristSpotSearchGateway;
import com.klist.chatbot.infrastructure.search.query.RetryingTouristSpotSearchGateway;
import com.klist.chatbot.infrastructure.search.query.TouristSpotSearchCacheProperties;
import com.klist.chatbot.observability.RetryEventListener;
import com.klist.chatbot.search.application.TouristSpotRetriever;
import com.klist.chatbot.search.application.TouristSpotSearchGateway;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        TouristSpotIndexProperties.class,
        TouristSpotSearchCacheProperties.class
})
public class TouristSpotIndexConfiguration {

    @Bean
    TouristSpotIndexResourceLoader touristSpotIndexResourceLoader(
            TouristSpotIndexProperties properties
    ) {
        return new TouristSpotIndexResourceLoader(new ObjectMapper(), properties);
    }

    @Bean
    TouristSpotIndexManager touristSpotIndexManager(
            ElasticsearchOperations operations,
            TouristSpotIndexProperties properties,
            TouristSpotIndexResourceLoader resourceLoader
    ) {
        return new TouristSpotIndexManager(operations, properties, resourceLoader);
    }

    @Bean
    TouristSpotIndexingGateway touristSpotIndexingGateway(
            ElasticsearchOperations operations,
            TouristSpotIndexProperties properties
    ) {
        return new ElasticsearchTouristSpotIndexingGateway(operations, properties);
    }

    @Bean
    TouristSpotSearchGateway touristSpotSearchGateway(
            ElasticsearchOperations operations,
            TouristSpotIndexProperties properties,
            RetryEventListener retryEvents,
            StringRedisTemplate redisTemplate,
            TouristSpotSearchCacheProperties cacheProperties
    ) {
        TouristSpotSearchGateway gateway =
                new ElasticsearchTouristSpotSearchGateway(operations, properties);
        TouristSpotSearchGateway retryingGateway = new RetryingTouristSpotSearchGateway(
                gateway,
                properties.getRetryMaxAttempts(),
                properties.getRetryInitialBackoff(),
                properties.getRetryMaxBackoff(),
                retryEvents
        );
        return new CachingTouristSpotSearchGateway(
                retryingGateway,
                redisTemplate,
                new ObjectMapper(),
                cacheProperties
        );
    }

    @Bean
    TouristSpotRetriever touristSpotRetriever(TouristSpotSearchGateway searchGateway) {
        return new TouristSpotRetriever(searchGateway);
    }
}
