package com.klist.chatbot.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.klist.chatbot.chat.application.ChatSearchOrchestrator;
import com.klist.chatbot.chat.application.prompt.ChatPromptPreparationStatus;
import com.klist.chatbot.infrastructure.search.document.TouristSpotSearchDocument;
import com.klist.chatbot.infrastructure.search.document.TouristSpotSearchCategory;
import com.klist.chatbot.infrastructure.search.document.TouristSpotSearchRegion;
import com.klist.chatbot.infrastructure.search.index.TouristSpotIndexInitializationResult;
import com.klist.chatbot.infrastructure.search.index.TouristSpotIndexManager;
import com.klist.chatbot.infrastructure.search.index.TouristSpotIndexingGateway;
import com.klist.chatbot.search.application.TouristSpotSearchCriteria;
import com.klist.chatbot.search.application.TouristSpotSearchGateway;
import com.klist.chatbot.search.application.TouristSpotSearchResult;
import com.klist.chatbot.search.fixture.TouristSpotSearchQualityFixture;
import com.klist.chatbot.search.fixture.TourApiSampleSearchQualityFixture;
import com.klist.chatbot.search.fixture.TouristSpotSearchQualityEvaluator;
import com.klist.chatbot.search.fixture.SearchQualityCase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.SoftAssertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("elasticsearch")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class TouristSpotIndexingIntegrationTest {

    @Container
    static final ElasticsearchContainer ELASTICSEARCH =
            new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:9.4.2")
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("discovery.type", "single-node")
                    .withCommand(
                            "bash",
                            "-c",
                            "bin/elasticsearch-plugin install --batch analysis-nori"
                                    + " && /usr/local/bin/docker-entrypoint.sh eswrapper"
                    );

    @DynamicPropertySource
    static void elasticsearchProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", ELASTICSEARCH::getHttpHostAddress);
    }

    @Autowired
    private TouristSpotIndexManager indexManager;

    @Autowired
    private TouristSpotIndexingGateway gateway;

    @Autowired
    private TouristSpotSearchGateway searchGateway;

    @Autowired
    private ChatSearchOrchestrator chatSearchOrchestrator;

    @Autowired
    private ElasticsearchOperations operations;

    @BeforeEach
    void initializeIndex() {
        indexManager.initialize();
    }

    @Test
    void createsMappingAndUsesAliasForGatewayOperations() {
        TouristSpotIndexInitializationResult initialization = indexManager.initialize();

        assertThat(initialization.indexName()).isEqualTo("tourist-spots-v1");
        assertThat(operations.indexOps(IndexCoordinates.of("tourist-spots-v1")).exists()).isTrue();

        Map<String, Object> mapping = operations.indexOps(IndexCoordinates.of("tourist-spots-v1")).getMapping();
        Map<String, Object> fields = map(mapping.get("properties"));
        assertThat(map(fields.get("coordinates"))).containsEntry("type", "geo_point");
        assertThat(map(map(fields.get("title")).get("fields")))
                .containsKey("keyword");

        TouristSpotSearchDocument first = document(1001L, "경복궁");
        TouristSpotSearchDocument second = document(1002L, "창덕궁");
        gateway.save(first);
        gateway.saveAll(List.of(second));

        assertThat(gateway.exists(1001L)).isTrue();
        assertThat(gateway.exists(1002L)).isTrue();

        gateway.delete(1001L);
        assertThat(gateway.exists(1001L)).isFalse();
    }

    @Test
    void searchesKoreanKeywordWithRegionCategoryDistanceAndMinimumScore() {
        gateway.save(searchDocument(
                2001L,
                "북악산 야경 전망대",
                "서울 도심의 야경을 감상하기 좋은 전망 명소",
                "서울특별시 종로구",
                11L,
                "1",
                "1",
                12,
                37.5928,
                126.9669
        ));
        gateway.save(searchDocument(
                2002L,
                "제주 해변 산책로",
                "제주의 바다를 따라 걷는 산책 명소",
                "제주특별자치도 제주시",
                50L,
                "39",
                "4",
                12,
                33.4996,
                126.5312
        ));
        operations.indexOps(IndexCoordinates.of("tourist-spots-v1")).refresh();

        TouristSpotSearchResult result = searchGateway.search(new TouristSpotSearchCriteria(
                "야경",
                11L,
                "1",
                "1",
                12,
                null,
                null,
                null,
                null,
                37.5665,
                126.9780,
                10.0,
                5,
                0.1f
        ));

        assertThat(result.totalHits()).isEqualTo(1);
        assertThat(result.evidence()).singleElement().satisfies(evidence -> {
            assertThat(evidence.touristSpotId()).isEqualTo(2001L);
            assertThat(evidence.title()).isEqualTo("북악산 야경 전망대");
            assertThat(evidence.regionId()).isEqualTo(11L);
            assertThat(evidence.contentTypeId()).isEqualTo(12);
            assertThat(evidence.score()).isPositive();
        });

        TouristSpotSearchResult belowMinimumScore = searchGateway.search(new TouristSpotSearchCriteria(
                "야경", null, null, null, null, null, null, null, null,
                null, null, null, 5, 1000.0f
        ));
        assertThat(belowMinimumScore.isEmpty()).isTrue();
    }

    @Test
    void ranksExpectedTouristSpotFirstForRepresentativeNaturalLanguageQuestions() {
        gateway.saveAll(TouristSpotSearchQualityFixture.documents());
        operations.indexOps(IndexCoordinates.of("tourist-spots-v1")).refresh();

        SoftAssertions.assertSoftly(softly -> {
            for (var qualityCase : TouristSpotSearchQualityFixture.cases()) {
                TouristSpotSearchResult result = searchGateway.search(qualityCase.criteria());
                softly.assertThat(result.evidence())
                        .as("question=%s", qualityCase.question())
                        .isNotEmpty();
                if (!result.evidence().isEmpty()) {
                    softly.assertThat(result.evidence().get(0).touristSpotId())
                            .as("Top-1 question=%s", qualityCase.question())
                            .isEqualTo(qualityCase.expectedTouristSpotId());
                }
            }
        });
    }

    @Test
    void reportsTopOneAndTopThreeQualityByRegionAndContentTypeUsingTourApiSamples() {
        var tourApiSamples = TourApiSampleSearchQualityFixture.load();
        List<TouristSpotSearchDocument> documents = new ArrayList<>(TouristSpotSearchQualityFixture.documents());
        documents.addAll(tourApiSamples.documents());
        gateway.saveAll(documents);
        operations.indexOps(IndexCoordinates.of("tourist-spots-v1")).refresh();

        List<SearchQualityCase> cases = new ArrayList<>(TouristSpotSearchQualityFixture.cases());
        cases.addAll(tourApiSamples.cases());
        var evaluation = TouristSpotSearchQualityEvaluator.evaluate(cases, searchGateway);

        assertThat(evaluation.overall().total()).isEqualTo(14);
        assertThat(evaluation.overall().top1Rate()).isGreaterThanOrEqualTo(0.9);
        assertThat(evaluation.overall().top3Rate()).isEqualTo(1.0);
        assertThat(evaluation.byRegion()).containsKeys("서울", "부산", "제주");
        assertThat(evaluation.byRegion().values())
                .allSatisfy(metrics -> assertThat(metrics.top3Rate()).isEqualTo(1.0));
        assertThat(evaluation.byContentType()).containsKeys("12", "14", "15", "25", "28", "32", "38", "39");
        assertThat(evaluation.byContentType().values())
                .allSatisfy(metrics -> assertThat(metrics.top3Rate()).isEqualTo(1.0));
    }

    @Test
    void analyzesChatQuestionsIntoCriteriaThatRetrieveExpectedTouristSpots() {
        gateway.saveAll(TouristSpotSearchQualityFixture.documents());
        operations.indexOps(IndexCoordinates.of("tourist-spots-v1")).refresh();

        assertAnalyzedTopOne("서울에서 야경 전망 명소 추천해줘",
                TouristSpotSearchQualityFixture.N_SEOUL_TOWER_ID);
        assertAnalyzedTopOne("부산에서 해변 데이트 명소 알려줘",
                TouristSpotSearchQualityFixture.HAEUNDAE_ID);
        assertAnalyzedTopOne("서울에서 비 오는 날 실내 미술관 찾아줘",
                TouristSpotSearchQualityFixture.SEOUL_MUSEUM_ID);
    }

    private void assertAnalyzedTopOne(String question, long expectedTouristSpotId) {
        var chatSearchResult = chatSearchOrchestrator.search(question);
        var analysis = chatSearchResult.questionAnalysis();
        TouristSpotSearchResult result = chatSearchResult.touristSpotSearchResult();

        assertThat(chatSearchResult.promptPreparation().status())
                .isEqualTo(ChatPromptPreparationStatus.READY);
        assertThat(chatSearchResult.promptPreparation().prompt().userMessage())
                .contains(question, "\"touristSpotId\":" + expectedTouristSpotId);
        assertThat(chatSearchResult.evidenceContext().touristSpots())
                .extracting(evidence -> evidence.touristSpotId())
                .containsExactlyElementsOf(result.evidence().stream()
                        .map(evidence -> evidence.touristSpotId())
                        .toList());
        assertThat(result.evidence())
                .as("question=%s keyword=%s", question, analysis.normalizedKeyword())
                .isNotEmpty();
        assertThat(result.evidence().get(0).touristSpotId()).isEqualTo(expectedTouristSpotId);
    }

    private static TouristSpotSearchDocument document(Long id, String title) {
        return new TouristSpotSearchDocument(
                id,
                title,
                "한국의 대표적인 궁궐",
                "서울특별시 종로구",
                null,
                null,
                new GeoPoint(37.5796, 126.9770),
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static TouristSpotSearchDocument searchDocument(
            Long id,
            String title,
            String description,
            String address,
            Long regionId,
            String areaCode,
            String sigunguCode,
            Integer contentTypeId,
            double latitude,
            double longitude
    ) {
        return new TouristSpotSearchDocument(
                id,
                title,
                description,
                address,
                new TouristSpotSearchRegion(regionId, areaCode, sigunguCode, null, null),
                new TouristSpotSearchCategory(null, contentTypeId, null, null, null),
                new GeoPoint(latitude, longitude),
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
