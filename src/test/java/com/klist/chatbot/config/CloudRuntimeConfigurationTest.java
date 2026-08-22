package com.klist.chatbot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class CloudRuntimeConfigurationTest {

    private static final String RESOURCE_NAME = "application-cloud.yml";

    @Test
    void definesDockerIndependentExternalServiceContract() throws IOException {
        Map<String, Object> properties = properties();

        assertString(properties, "spring.config.activate.on-profile", "cloud");
        assertString(properties, "spring.datasource.url", "${DB_URL}");
        assertString(properties, "spring.datasource.username", "${DB_USERNAME}");
        assertString(properties, "spring.datasource.password", "${DB_PASSWORD}");
        assertString(properties, "spring.elasticsearch.uris", "${ELASTICSEARCH_URIS}");
        assertString(properties, "spring.elasticsearch.username", "${ELASTICSEARCH_USERNAME}");
        assertString(properties, "spring.elasticsearch.password", "${ELASTICSEARCH_PASSWORD}");
        assertString(properties, "spring.data.redis.url", "${REDIS_URL}");
        assertString(properties, "spring.jpa.hibernate.ddl-auto", "validate");
        assertString(properties, "spring.flyway.enabled", "true");
        assertString(properties, "server.shutdown", "graceful");
        assertString(properties, "search.tourist-spots.bootstrap.mode",
                "${TOURIST_SPOT_INDEX_BOOTSTRAP_MODE:none}");
        assertString(properties, "search.tourist-spots.cache.enabled",
                "${TOURIST_SPOT_SEARCH_CACHE_ENABLED:true}");
        assertString(properties, "search.tourist-spots.cache.ttl",
                "${TOURIST_SPOT_SEARCH_CACHE_TTL:5m}");
        assertString(properties, "logging.structured.format.console",
                "${LOGGING_STRUCTURED_FORMAT_CONSOLE:logstash}");
        assertString(properties, "logging.structured.json.context.include", "true");
    }

    @Test
    void containsNoLocalInfrastructureOrEmbeddedSecretDefaults() throws IOException {
        Map<String, Object> properties = properties();

        assertThat(properties.values())
                .map(String::valueOf)
                .noneMatch(value -> value.contains("localhost") || value.contains("127.0.0.1"));
        assertString(properties, "tour-api.service-key", "${TOUR_API_SERVICE_KEY:}");
        assertString(properties, "tour-api.ingestion.schedule.enabled",
                "${TOUR_API_INGESTION_SCHEDULE_ENABLED:false}");
        assertString(properties, "tour-api.ingestion.schedule.lock-key",
                "${TOUR_API_INGESTION_LOCK_KEY:klist:tourapi:ingestion:schedule}");
        assertString(properties, "tour-api.ingestion.schedule.lock-ttl",
                "${TOUR_API_INGESTION_LOCK_TTL:2h}");
    }

    private static Map<String, Object> properties() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(
                "cloudRuntime",
                new ClassPathResource(RESOURCE_NAME)
        );
        assertThat(sources).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> source = (Map<String, Object>) sources.get(0).getSource();
        return source;
    }

    private static void assertString(Map<String, Object> properties, String key, String expected) {
        assertThat(properties.get(key)).as(key).hasToString(expected);
    }
}
