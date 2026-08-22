package com.klist.chatbot.tourapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.klist.chatbot.infrastructure.tourapi.ingestion.TourApiIngestionService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.sql.init.mode=never",
        "tour-api.ingestion.enabled=false"
})
@ActiveProfiles("test")
@Import(TourApiLivePostgreSqlIntegrationTest.ContainerConfiguration.class)
@Tag("tourapi-live")
@Tag("postgresql")
@EnabledIfEnvironmentVariable(named = "TOUR_API_SERVICE_KEY", matches = ".+")
class TourApiLivePostgreSqlIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(
            TourApiLivePostgreSqlIntegrationTest.class
    );

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("tour-api.service-key", () -> System.getenv("TOUR_API_SERVICE_KEY"));
    }

    @Autowired
    private TourApiIngestionService ingestionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ingestsAndValidatesFiftyRealTourApiItemsIdempotently() {
        var first = ingestionService.ingestAll(50, 1, 50);

        assertThat(first.completedPages()).isEqualTo(1);
        assertThat(first.sourceItems()).isEqualTo(50);
        assertThat(first.total()).isEqualTo(50);
        assertThat(first.failedContentIds()).isEmpty();
        assertThat(first.failed()).isZero();
        assertThat(first.created()).isEqualTo(50);

        Map<String, Object> quality = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS total,
                       SUM(CASE WHEN name IS NULL OR TRIM(name) = '' THEN 1 ELSE 0 END) AS missing_name,
                       SUM(CASE WHEN source_modified_at IS NULL THEN 1 ELSE 0 END) AS missing_modified_at,
                       SUM(CASE WHEN latitude IS NOT NULL AND (latitude < -90 OR latitude > 90)
                                THEN 1 ELSE 0 END) AS invalid_latitude,
                       SUM(CASE WHEN longitude IS NOT NULL AND (longitude < -180 OR longitude > 180)
                                THEN 1 ELSE 0 END) AS invalid_longitude,
                       SUM(CASE WHEN (latitude IS NULL AND longitude IS NOT NULL)
                                     OR (latitude IS NOT NULL AND longitude IS NULL)
                                THEN 1 ELSE 0 END) AS invalid_coordinate_pair,
                       SUM(CASE WHEN latitude IS NULL AND longitude IS NULL
                                THEN 1 ELSE 0 END) AS missing_coordinates,
                       SUM(CASE WHEN description IS NULL OR TRIM(description) = ''
                                THEN 1 ELSE 0 END) AS missing_description,
                       SUM(CASE WHEN area_code IS NULL OR TRIM(area_code) = ''
                                THEN 1 ELSE 0 END) AS missing_area_code,
                       SUM(CASE WHEN official_url IS NULL OR TRIM(official_url) = ''
                                THEN 1 ELSE 0 END) AS missing_official_url,
                       SUM(CASE WHEN official_url IS NOT NULL
                                     AND official_url NOT LIKE 'http://%'
                                     AND official_url NOT LIKE 'https://%'
                                THEN 1 ELSE 0 END) AS invalid_official_url,
                       COUNT(DISTINCT content_type_id) AS content_types,
                       COUNT(DISTINCT area_code) AS areas
                FROM tourist_spot
                """);

        assertThat(((Number) quality.get("total")).intValue()).isEqualTo(50);
        assertThat(((Number) quality.get("missing_name")).intValue()).isZero();
        assertThat(((Number) quality.get("missing_modified_at")).intValue()).isZero();
        assertThat(((Number) quality.get("invalid_latitude")).intValue()).isZero();
        assertThat(((Number) quality.get("invalid_longitude")).intValue()).isZero();
        assertThat(((Number) quality.get("invalid_coordinate_pair")).intValue()).isZero();
        assertThat(((Number) quality.get("invalid_official_url")).intValue()).isZero();
        assertThat(((Number) quality.get("content_types")).intValue()).isPositive();
        assertThat(((Number) quality.get("areas")).intValue()).isPositive();

        List<Map<String, Object>> contentTypeCounts = jdbcTemplate.queryForList("""
                SELECT content_type_id, COUNT(*) AS item_count
                FROM tourist_spot
                GROUP BY content_type_id
                ORDER BY content_type_id
                """);
        List<Map<String, Object>> areaCounts = jdbcTemplate.queryForList("""
                SELECT COALESCE(NULLIF(TRIM(area_code), ''), '(missing)') AS area_code,
                       COUNT(*) AS item_count
                FROM tourist_spot
                GROUP BY COALESCE(NULLIF(TRIM(area_code), ''), '(missing)')
                ORDER BY area_code
                """);
        assertThat(sumCounts(contentTypeCounts)).isEqualTo(50);
        assertThat(sumCounts(areaCounts)).isEqualTo(50);
        log.info("TourAPI 50-item quality metrics. metrics={}", quality);
        log.info("TourAPI 50-item distribution. contentTypes={}, areas={}",
                contentTypeCounts, areaCounts);

        var second = ingestionService.ingestAll(50, 1, 50);

        assertThat(second.total()).isEqualTo(50);
        assertThat(second.failed()).isZero();
        assertThat(second.created()).isZero();
        assertThat(second.updated() + second.skipped()).isEqualTo(50);
        log.info("TourAPI 50-item idempotency result. first={}, second={}", first, second);
    }

    private int sumCounts(List<Map<String, Object>> counts) {
        return counts.stream()
                .mapToInt(row -> ((Number) row.get("item_count")).intValue())
                .sum();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ContainerConfiguration {

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgreSqlContainer() {
            return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
        }
    }
}
