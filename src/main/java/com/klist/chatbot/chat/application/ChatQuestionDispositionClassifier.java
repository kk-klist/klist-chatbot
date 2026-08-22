package com.klist.chatbot.chat.application;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class ChatQuestionDispositionClassifier {

    private static final List<String> UNSUPPORTED_TERMS = List.of(
            "주가", "주식", "코인", "환율", "코딩", "프로그래밍", "번역",
            "수학", "의학", "진단", "처방", "법률", "소송", "정치", "대통령",
            "날씨", "기온", "미세먼지", "운세", "노래"
    );
    private static final List<String> TOURISM_TERMS = List.of(
            "관광", "여행", "명소", "맛집", "식당", "음식점", "카페", "숙소",
            "숙박", "호텔", "펜션", "리조트", "축제", "박물관", "미술관", "공연",
            "쇼핑", "백화점", "레포츠", "해수욕장", "공원", "코스"
    );
    private static final Pattern VAGUE_REQUEST = Pattern.compile(
            "^(?:어디(?:가|로)?\\s*(?:좋아|갈까|가볼까)?|"
                    + "뭐(?:가|를)?\\s*(?:하지|할까|볼까)?|"
                    + "추천(?:해|해줘|해주세요)?|"
                    + "(?:관광지|여행지|맛집|숙소)(?:를|을)?\\s*추천(?:해|해줘|해주세요)?|"
                    + "가볼\\s*만한\\s*(?:곳|장소)(?:을|를)?\\s*(?:추천(?:해|해줘|해주세요)?)?)"
                    + "[?!. ]*$"
    );

    public ChatQuestionDisposition classify(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank.");
        }
        String normalized = question.trim().replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
        if (VAGUE_REQUEST.matcher(normalized).matches()) {
            return ChatQuestionDisposition.CLARIFICATION_REQUIRED;
        }
        boolean unsupported = UNSUPPORTED_TERMS.stream().anyMatch(normalized::contains);
        boolean tourism = TOURISM_TERMS.stream().anyMatch(normalized::contains);
        if (unsupported && !tourism) {
            return ChatQuestionDisposition.UNSUPPORTED;
        }
        return ChatQuestionDisposition.SEARCH;
    }
}
