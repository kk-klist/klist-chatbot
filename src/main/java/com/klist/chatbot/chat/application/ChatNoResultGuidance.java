package com.klist.chatbot.chat.application;

import com.klist.chatbot.chat.application.analysis.ChatQuestionAnalysis;
import com.klist.chatbot.search.application.TouristSpotSearchCriteria;

final class ChatNoResultGuidance {

    private static final String PREFIX = "조건에 맞는 관광지를 찾지 못했습니다. ";
    private static final String DEFAULT_GUIDANCE =
            PREFIX + "다른 지역이나 관광 유형으로 질문해 주세요.";

    private ChatNoResultGuidance() {
    }

    static String message(ChatQuestionAnalysis analysis) {
        if (analysis == null) {
            return DEFAULT_GUIDANCE;
        }

        TouristSpotSearchCriteria criteria = analysis.searchCriteria();
        if (criteria != null && criteria.hasDistance()) {
            return PREFIX + "검색 반경을 넓히거나 위치 조건을 빼고 다시 질문해 주세요.";
        }

        boolean hasRegion = hasRegion(analysis, criteria);
        boolean hasContentType = hasContentType(analysis, criteria);
        if (hasRegion && hasContentType) {
            return PREFIX + "지역 또는 관광 유형 조건 중 하나를 빼고 다시 질문해 주세요.";
        }
        if (hasRegion) {
            if (analysis.detectedRegion() != null && !analysis.detectedRegion().isBlank()) {
                return PREFIX + analysis.detectedRegion()
                        + " 인접 지역이나 다른 지역으로 범위를 넓혀 질문해 주세요.";
            }
            return PREFIX + "인접 지역이나 다른 지역으로 범위를 넓혀 질문해 주세요.";
        }
        if (hasContentType) {
            return PREFIX + "다른 관광 유형을 포함해 질문해 주세요.";
        }
        if (analysis.normalizedKeyword() != null && !analysis.normalizedKeyword().isBlank()) {
            return PREFIX + "검색어를 더 짧고 일반적인 표현으로 바꿔 질문해 주세요.";
        }
        return DEFAULT_GUIDANCE;
    }

    private static boolean hasRegion(
            ChatQuestionAnalysis analysis,
            TouristSpotSearchCriteria criteria
    ) {
        return analysis.detectedRegion() != null
                || criteria != null && (criteria.regionId() != null
                || criteria.areaCode() != null
                || criteria.sigunguCode() != null);
    }

    private static boolean hasContentType(
            ChatQuestionAnalysis analysis,
            TouristSpotSearchCriteria criteria
    ) {
        return analysis.detectedContentTypeId() != null
                || criteria != null && (criteria.contentTypeId() != null
                || criteria.categoryId() != null
                || criteria.largeCategoryCode() != null
                || criteria.middleCategoryCode() != null
                || criteria.smallCategoryCode() != null);
    }
}
