package com.klist.chatbot.chat.application.analysis;

import com.klist.chatbot.search.application.TouristSpotSearchCriteria;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public class ChatQuestionAnalyzer {

    private static final List<RegionRule> REGION_RULES = List.of(
            region("서울", "1", "서울특별시", "서울시", "서울"),
            region("인천", "2", "인천광역시", "인천시", "인천"),
            region("대전", "3", "대전광역시", "대전시", "대전"),
            region("대구", "4", "대구광역시", "대구시", "대구"),
            region("광주", "5", "광주광역시", "광주시", "광주"),
            region("부산", "6", "부산광역시", "부산시", "부산"),
            region("울산", "7", "울산광역시", "울산시", "울산"),
            region("세종", "8", "세종특별자치시", "세종시", "세종"),
            region("경기", "31", "경기도", "경기"),
            region("강원", "32", "강원특별자치도", "강원도", "강원"),
            region("충북", "33", "충청북도", "충북"),
            region("충남", "34", "충청남도", "충남"),
            region("경북", "35", "경상북도", "경북"),
            region("경남", "36", "경상남도", "경남"),
            region("전북", "37", "전북특별자치도", "전라북도", "전북"),
            region("전남", "38", "전라남도", "전남"),
            region("제주", "39", "제주특별자치도", "제주도", "제주")
    );

    private static final List<ContentTypeRule> CONTENT_TYPE_RULES = List.of(
            contentType(14, "미술관", "박물관", "전시관", "문화시설", "공연장"),
            contentType(15, "축제", "페스티벌", "지역행사", "문화행사"),
            contentType(25, "여행코스", "여행 코스", "관광코스", "관광 코스"),
            contentType(28, "레포츠", "카약", "서핑", "스키", "래프팅", "수상레저"),
            contentType(32, "호텔", "숙소", "숙박", "펜션", "리조트", "게스트하우스"),
            contentType(38, "쇼핑", "백화점", "면세점", "아울렛"),
            contentType(39, "맛집", "식당", "음식점", "레스토랑", "카페")
    );

    private static final List<Pattern> INTENT_PATTERNS = List.of(
            Pattern.compile("추천(?:해|하여)?\\s*(?:줘|주세요)?"),
            Pattern.compile("알려\\s*(?:줘|주세요)"),
            Pattern.compile("찾아\\s*(?:줘|주세요)"),
            Pattern.compile("소개\\s*(?:해|하여)?\\s*(?:줘|주세요)?"),
            Pattern.compile("어디(?:가|를|로|에)?"),
            Pattern.compile("가볼\\s*만한\\s*곳"),
            Pattern.compile("갈\\s*만한\\s*곳"),
            Pattern.compile("방문(?:할|하기)\\s*만한\\s*(?:곳|장소)?"),
            Pattern.compile("(?:관광지|관광\\s*명소|명소)(?:를|을|가|이)?")
    );

    private final ChatQuestionAnalysisProperties properties;

    public ChatQuestionAnalyzer(ChatQuestionAnalysisProperties properties) {
        this.properties = properties;
    }

    public ChatQuestionAnalysis analyze(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank.");
        }
        properties.validate();
        String original = normalizeWhitespace(question);
        Match<RegionRule> regionMatch = firstMatch(original, REGION_RULES, RegionRule::aliases);
        Match<ContentTypeRule> contentTypeMatch = firstMatch(original, CONTENT_TYPE_RULES, ContentTypeRule::keywords);
        String keyword = normalizeKeyword(original, regionMatch);
        if (keyword == null && regionMatch == null && contentTypeMatch == null) {
            keyword = original;
        }

        RegionRule region = regionMatch == null ? null : regionMatch.rule();
        ContentTypeRule contentType = contentTypeMatch == null ? null : contentTypeMatch.rule();
        TouristSpotSearchCriteria criteria = new TouristSpotSearchCriteria(
                keyword,
                null,
                region == null ? null : region.areaCode(),
                null,
                contentType == null ? null : contentType.contentTypeId(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                properties.getResultSize(),
                properties.getMinimumScore()
        );
        return new ChatQuestionAnalysis(
                original,
                keyword,
                region == null ? null : region.name(),
                contentType == null ? null : contentType.contentTypeId(),
                criteria
        );
    }

    private String normalizeKeyword(String question, Match<RegionRule> regionMatch) {
        String keyword = question;
        if (regionMatch != null) {
            keyword = keyword.replaceFirst(
                    Pattern.quote(regionMatch.alias()) + "(?:에서|으로|에|의|근처|주변)?",
                    " "
            );
        }
        for (Pattern pattern : INTENT_PATTERNS) {
            keyword = pattern.matcher(keyword).replaceAll(" ");
        }
        keyword = keyword.replaceAll("[?!.]+", " ");
        keyword = normalizeWhitespace(keyword);
        return keyword.isBlank() ? null : keyword;
    }

    private static <T> Match<T> firstMatch(
            String question,
            List<T> rules,
            java.util.function.Function<T, List<String>> terms
    ) {
        return rules.stream()
                .flatMap(rule -> terms.apply(rule).stream()
                        .map(term -> new Match<>(rule, term, question.indexOf(term))))
                .filter(match -> match.position() >= 0)
                .min(Comparator.comparingInt(Match<T>::position)
                        .thenComparing(match -> -match.alias().length()))
                .orElse(null);
    }

    private static String normalizeWhitespace(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private static RegionRule region(String name, String areaCode, String... aliases) {
        return new RegionRule(name, areaCode, List.of(aliases));
    }

    private static ContentTypeRule contentType(int contentTypeId, String... keywords) {
        return new ContentTypeRule(contentTypeId, List.of(keywords));
    }

    private record Match<T>(T rule, String alias, int position) {
    }

    private record RegionRule(String name, String areaCode, List<String> aliases) {
        private RegionRule {
            Objects.requireNonNull(name);
            Objects.requireNonNull(areaCode);
            aliases = List.copyOf(aliases);
        }
    }

    private record ContentTypeRule(int contentTypeId, List<String> keywords) {
        private ContentTypeRule {
            keywords = List.copyOf(keywords);
        }
    }
}
