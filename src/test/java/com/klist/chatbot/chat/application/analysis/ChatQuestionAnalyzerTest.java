package com.klist.chatbot.chat.application.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ChatQuestionAnalyzerTest {

    private ChatQuestionAnalysisProperties properties;
    private ChatQuestionAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        properties = new ChatQuestionAnalysisProperties();
        analyzer = new ChatQuestionAnalyzer(properties);
    }

    @ParameterizedTest
    @MethodSource("regionCases")
    void extractsTourApiAreaCodeAndRemovesRegionFromKeyword(
            String question,
            String expectedRegion,
            String expectedAreaCode
    ) {
        ChatQuestionAnalysis analysis = analyzer.analyze(question);

        assertThat(analysis.detectedRegion()).isEqualTo(expectedRegion);
        assertThat(analysis.searchCriteria().areaCode()).isEqualTo(expectedAreaCode);
        assertThat(analysis.normalizedKeyword()).doesNotContain(expectedRegion);
        assertThat(analysis.normalizedKeyword()).doesNotContain("추천해줘", "알려줘");
    }

    @ParameterizedTest
    @MethodSource("contentTypeCases")
    void extractsContentTypeFromTourismIntent(String question, int expectedContentTypeId) {
        ChatQuestionAnalysis analysis = analyzer.analyze(question);

        assertThat(analysis.detectedContentTypeId()).isEqualTo(expectedContentTypeId);
        assertThat(analysis.searchCriteria().contentTypeId()).isEqualTo(expectedContentTypeId);
    }

    @Test
    void preservesUnknownTermsForElasticsearchKeywordSearch() {
        ChatQuestionAnalysis analysis = analyzer.analyze("조용하고 사진 찍기 좋은 곳 찾아줘");

        assertThat(analysis.normalizedKeyword()).isEqualTo("조용하고 사진 찍기 좋은 곳");
        assertThat(analysis.detectedRegion()).isNull();
        assertThat(analysis.detectedContentTypeId()).isNull();
    }

    @Test
    void allowsRegionOnlySearchWhenIntentWordsAreRemoved() {
        ChatQuestionAnalysis analysis = analyzer.analyze("제주도에서 추천해줘");

        assertThat(analysis.normalizedKeyword()).isNull();
        assertThat(analysis.searchCriteria().areaCode()).isEqualTo("39");
    }

    @Test
    void removesGenericTouristSpotRequestWordsForRegionOnlySearch() {
        ChatQuestionAnalysis analysis = analyzer.analyze(
                "서울에서 방문할 만한 관광지를 추천해줘"
        );

        assertThat(analysis.normalizedKeyword()).isNull();
        assertThat(analysis.detectedRegion()).isEqualTo("서울");
        assertThat(analysis.searchCriteria().areaCode()).isEqualTo("1");
    }

    @Test
    void preservesSpecificKeywordWhileRemovingGenericTouristSpotWords() {
        ChatQuestionAnalysis analysis = analyzer.analyze("서울 야경 명소 추천해줘");

        assertThat(analysis.normalizedKeyword()).isEqualTo("야경");
        assertThat(analysis.searchCriteria().areaCode()).isEqualTo("1");
    }

    @Test
    void usesConfiguredResultLimitAndMinimumScore() {
        properties.setResultSize(8);
        properties.setMinimumScore(0.5f);

        ChatQuestionAnalysis analysis = analyzer.analyze("부산 맛집 알려줘");

        assertThat(analysis.searchCriteria().size()).isEqualTo(8);
        assertThat(analysis.searchCriteria().minimumScore()).isEqualTo(0.5f);
    }

    @Test
    void rejectsBlankQuestionAndInvalidConfiguration() {
        assertThatThrownBy(() -> analyzer.analyze(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("question must not be blank.");

        properties.setResultSize(0);
        assertThatThrownBy(() -> analyzer.analyze("서울 야경"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("result size");
    }

    private static Stream<Arguments> regionCases() {
        return Stream.of(
                Arguments.of("서울에서 야경 명소 추천해줘", "서울", "1"),
                Arguments.of("부산광역시 맛집 알려줘", "부산", "6"),
                Arguments.of("제주도 가족 숙소 추천해줘", "제주", "39"),
                Arguments.of("강원특별자치도에서 스키장 찾아줘", "강원", "32"),
                Arguments.of("전북특별자치도 여행 코스 알려줘", "전북", "37")
        );
    }

    private static Stream<Arguments> contentTypeCases() {
        return Stream.of(
                Arguments.of("서울 실내 미술관 추천", 14),
                Arguments.of("부산 지역 축제 알려줘", 15),
                Arguments.of("제주 여행 코스 추천", 25),
                Arguments.of("강원 스키 레포츠", 28),
                Arguments.of("가족 호텔 찾아줘", 32),
                Arguments.of("서울 백화점 쇼핑", 38),
                Arguments.of("부산 맛집 추천", 39)
        );
    }
}
