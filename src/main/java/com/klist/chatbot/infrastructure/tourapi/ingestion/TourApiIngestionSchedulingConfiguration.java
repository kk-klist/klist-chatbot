package com.klist.chatbot.infrastructure.tourapi.ingestion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(TourApiIngestionScheduleProperties.class)
@ConditionalOnProperty(
        prefix = "tour-api.ingestion.schedule",
        name = "enabled",
        havingValue = "true"
)
public class TourApiIngestionSchedulingConfiguration {

    @Bean
    TourApiIngestionScheduler tourApiIngestionScheduler(
            TourApiIngestionService ingestionService,
            TourApiIngestionProperties ingestionProperties,
            TourApiIngestionLock ingestionLock
    ) {
        return new TourApiIngestionScheduler(ingestionService, ingestionProperties, ingestionLock);
    }

    @Bean
    TourApiIngestionLock tourApiIngestionLock(
            StringRedisTemplate redisTemplate,
            TourApiIngestionScheduleProperties properties
    ) {
        return new RedisTourApiIngestionLock(
                redisTemplate,
                properties.getLockKey(),
                properties.getLockTtl()
        );
    }
}
