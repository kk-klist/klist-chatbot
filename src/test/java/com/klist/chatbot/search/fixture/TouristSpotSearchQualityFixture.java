package com.klist.chatbot.search.fixture;

import com.klist.chatbot.infrastructure.search.document.TouristSpotSearchCategory;
import com.klist.chatbot.infrastructure.search.document.TouristSpotSearchDocument;
import com.klist.chatbot.infrastructure.search.document.TouristSpotSearchRegion;
import com.klist.chatbot.search.application.TouristSpotSearchCriteria;
import java.util.List;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;

public final class TouristSpotSearchQualityFixture {

    public static final long N_SEOUL_TOWER_ID = 5001L;
    public static final long JEJU_FOLK_VILLAGE_ID = 5002L;
    public static final long SEOUL_MUSEUM_ID = 5003L;
    public static final long HAEUNDAE_ID = 5004L;
    public static final long GYEONGBOKGUNG_ID = 5005L;

    private TouristSpotSearchQualityFixture() {
    }

    public static List<TouristSpotSearchDocument> documents() {
        return List.of(
                document(
                        N_SEOUL_TOWER_ID,
                        "N서울타워",
                        "서울 야경과 도심 전망을 감상하는 대표 명소이자 연인 데이트 장소",
                        "서울특별시 용산구 남산공원길",
                        11L, "1", "21", 12, 37.5512, 126.9882,
                        "매일 10:00~23:00", "전망대 입장료 별도"
                ),
                document(
                        JEJU_FOLK_VILLAGE_ID,
                        "제주민속촌",
                        "제주 전통문화를 배우고 체험하는 가족 여행지",
                        "제주특별자치도 서귀포시 표선면",
                        50L, "39", "3", 12, 33.3225, 126.8414,
                        "매일 08:30~18:00", "성인 입장료 15000원"
                ),
                document(
                        SEOUL_MUSEUM_ID,
                        "국립현대미술관 서울",
                        "비 오는 날 방문하기 좋은 서울 실내 미술관과 전시 문화 공간",
                        "서울특별시 종로구 삼청로",
                        11L, "1", "1", 14, 37.5786, 126.9800,
                        "화요일부터 일요일 10:00~18:00", "전시별 입장료 상이"
                ),
                document(
                        HAEUNDAE_ID,
                        "해운대해수욕장",
                        "부산 바다를 보며 산책하는 해변 데이트 명소",
                        "부산광역시 해운대구 해운대해변로",
                        26L, "6", "16", 12, 35.1587, 129.1604,
                        "상시 개방", "무료"
                ),
                document(
                        GYEONGBOKGUNG_ID,
                        "경복궁",
                        "조선 왕조의 역사와 건축을 살펴보는 서울 대표 궁궐 관광지",
                        "서울특별시 종로구 사직로",
                        11L, "1", "1", 12, 37.5796, 126.9770,
                        "수요일부터 월요일 09:00~18:00", "입장료 성인 3000원"
                ),
                document(
                        5006L,
                        "서울 실내 놀이공원",
                        "비 오는 날에도 즐기는 가족 놀이와 데이트 공간",
                        "서울특별시 송파구 올림픽로",
                        11L, "1", "18", 12, 37.5111, 127.0982,
                        "매일 10:00~21:00", "이용권 별도"
                )
        );
    }

    public static List<SearchQualityCase> cases() {
        return List.of(
                qualityCase("서울 야경 전망 명소", N_SEOUL_TOWER_ID, "서울", 12),
                qualityCase("제주 가족 체험 여행지", JEJU_FOLK_VILLAGE_ID, "제주", 12),
                qualityCase("비 오는 날 실내 미술관", SEOUL_MUSEUM_ID, "서울", 14),
                qualityCase("부산 해변 데이트 명소", HAEUNDAE_ID, "부산", 12),
                qualityCase("경복궁 입장료", GYEONGBOKGUNG_ID, "서울", 12),
                qualityCase("국립현대미술관 서울", SEOUL_MUSEUM_ID, "서울", 14)
        );
    }

    private static SearchQualityCase qualityCase(
            String question,
            long expectedTouristSpotId,
            String region,
            int contentTypeId
    ) {
        return new SearchQualityCase(
                question,
                expectedTouristSpotId,
                region,
                contentTypeId,
                new TouristSpotSearchCriteria(
                        question, null, null, null, null, null, null, null, null,
                        null, null, null, 5, null
                )
        );
    }

    private static TouristSpotSearchDocument document(
            long id,
            String title,
            String description,
            String address,
            long regionId,
            String areaCode,
            String sigunguCode,
            int contentTypeId,
            double latitude,
            double longitude,
            String openingHours,
            String admissionFee
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
                openingHours,
                admissionFee,
                null,
                null
        );
    }

}
