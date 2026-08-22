package com.klist.chatbot.search.application;

import com.klist.chatbot.domain.touristspot.repository.TouristSpotRepository;
import com.klist.chatbot.infrastructure.search.index.TouristSpotIndexManager;
import com.klist.chatbot.infrastructure.search.index.TouristSpotIndexingGateway;
import com.klist.chatbot.infrastructure.search.mapper.TouristSpotSearchDocumentMapper;
import com.klist.chatbot.infrastructure.search.failure.TouristSpotIndexFailureRecorder;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        TouristSpotReindexProperties.class,
        TouristSpotIndexBootstrapProperties.class
})
public class TouristSpotReindexConfiguration {

    @Bean
    TouristSpotFullReindexService touristSpotFullReindexService(
            TouristSpotRepository repository,
            TouristSpotSearchDocumentMapper mapper,
            TouristSpotIndexingGateway indexingGateway,
            TouristSpotIndexManager indexManager,
            TouristSpotReindexProperties properties,
            TouristSpotIndexFailureRecorder failureRecorder
    ) {
        return new TouristSpotFullReindexService(
                repository,
                mapper,
                indexingGateway,
                indexManager,
                properties,
                Clock.systemUTC(),
                failureRecorder
        );
    }

    @Bean
    TouristSpotIndexBootstrapRunner touristSpotIndexBootstrapRunner(
            TouristSpotIndexManager indexManager,
            TouristSpotFullReindexService reindexService,
            TouristSpotIndexBootstrapProperties properties
    ) {
        return new TouristSpotIndexBootstrapRunner(indexManager, reindexService, properties);
    }
}
