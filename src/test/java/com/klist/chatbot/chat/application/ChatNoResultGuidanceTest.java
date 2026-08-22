package com.klist.chatbot.chat.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.klist.chatbot.chat.application.analysis.ChatQuestionAnalysis;
import com.klist.chatbot.search.application.TouristSpotSearchCriteria;
import org.junit.jupiter.api.Test;

class ChatNoResultGuidanceTest {

    @Test
    void asksToExpandDistanceBeforeOtherConditions() {
        ChatQuestionAnalysis analysis = analysis(
                "서울 주변 1km 박물관",
                "서울",
                14,
                criteria("박물관", null, 14, 37.5, 127.0, 1.0)
        );

        assertThat(ChatNoResultGuidance.message(analysis))
                .contains("검색 반경을 넓히거나 위치 조건을 빼고");
    }

    @Test
    void asksToRemoveEitherRegionOrContentTypeWhenBothWereDetected() {
        ChatQuestionAnalysis analysis = analysis(
                "서울 박물관",
                "서울",
                14,
                criteria("박물관", "1", 14, null, null, null)
        );

        assertThat(ChatNoResultGuidance.message(analysis))
                .contains("지역 또는 관광 유형 조건 중 하나를 빼고");
    }

    @Test
    void suggestsAdjacentAreaForRegionOnlySearch() {
        ChatQuestionAnalysis analysis = analysis(
                "서울 관광지",
                "서울",
                null,
                criteria("관광지", "1", null, null, null, null)
        );

        assertThat(ChatNoResultGuidance.message(analysis))
                .contains("서울 인접 지역이나 다른 지역으로 범위를 넓혀");
    }

    @Test
    void suggestsBroaderKeywordWhenNoFilterWasDetected() {
        ChatQuestionAnalysis analysis = analysis(
                "조용한 산책 명소",
                null,
                null,
                criteria("조용한 산책 명소", null, null, null, null, null)
        );

        assertThat(ChatNoResultGuidance.message(analysis))
                .contains("검색어를 더 짧고 일반적인 표현으로");
    }

    @Test
    void keepsSafeDefaultWhenAnalysisIsUnavailable() {
        assertThat(ChatNoResultGuidance.message(null)).isEqualTo(
                "조건에 맞는 관광지를 찾지 못했습니다. 다른 지역이나 관광 유형으로 질문해 주세요."
        );
    }

    private static ChatQuestionAnalysis analysis(
            String keyword,
            String region,
            Integer contentTypeId,
            TouristSpotSearchCriteria criteria
    ) {
        return new ChatQuestionAnalysis(keyword, keyword, region, contentTypeId, criteria);
    }

    private static TouristSpotSearchCriteria criteria(
            String keyword,
            String areaCode,
            Integer contentTypeId,
            Double latitude,
            Double longitude,
            Double radiusKm
    ) {
        return new TouristSpotSearchCriteria(
                keyword, null, areaCode, null, contentTypeId, null,
                null, null, null, latitude, longitude, radiusKm, 5, 0.1f
        );
    }
}
