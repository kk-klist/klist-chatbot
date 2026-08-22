package com.klist.chatbot.chat.application.answer;

import com.klist.chatbot.chat.application.evidence.ChatEvidenceContext;
import com.klist.chatbot.chat.application.evidence.ChatTouristSpotEvidence;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatGeneratedAnswerValidator {

    private static final Pattern URL = Pattern.compile("https?://[^\\s\\])}>\\\"']+");
    private static final Pattern TIME = Pattern.compile("(?:[01]?\\d|2[0-3]):[0-5]\\d");
    private static final Pattern FEE = Pattern.compile("무료|\\d[\\d,]*원");
    private static final Pattern PHONE = Pattern.compile("0\\d{1,2}-\\d{3,4}-\\d{4}");

    public ChatGeneratedAnswer validate(
            ChatGeneratedAnswer generatedAnswer,
            ChatEvidenceContext evidenceContext
    ) {
        Objects.requireNonNull(generatedAnswer, "generatedAnswer must not be null");
        Objects.requireNonNull(evidenceContext, "evidenceContext must not be null");

        Set<Long> evidenceIds = evidenceContext.touristSpots().stream()
                .map(ChatTouristSpotEvidence::touristSpotId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<Long> unsupportedIds = new LinkedHashSet<>();
        for (ChatRecommendation recommendation : generatedAnswer.recommendations()) {
            if (!evidenceIds.contains(recommendation.touristSpotId())) {
                unsupportedIds.add(recommendation.touristSpotId());
            }
        }
        if (!unsupportedIds.isEmpty()) {
            throw new ChatAnswerGroundingException(unsupportedIds);
        }
        validateText(generatedAnswer.answer(), evidenceContext.touristSpots());
        java.util.Map<Long, ChatTouristSpotEvidence> evidenceById = evidenceContext.touristSpots()
                .stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        ChatTouristSpotEvidence::touristSpotId,
                        Function.identity()
                ));
        generatedAnswer.recommendations().forEach(recommendation -> validateText(
                recommendation.reason(),
                List.of(evidenceById.get(recommendation.touristSpotId()))
        ));
        return generatedAnswer;
    }

    private static void validateText(String text, List<ChatTouristSpotEvidence> evidence) {
        validateClaims(text, evidence, URL,
                item -> java.util.Arrays.asList(item.imageUrl(), item.reservationUrl()),
                ChatGroundingViolation.UNSUPPORTED_URL);
        validateClaims(text, evidence, TIME,
                item -> java.util.Collections.singletonList(item.openingHours()),
                ChatGroundingViolation.UNSUPPORTED_OPENING_HOURS);
        validateClaims(text, evidence, FEE,
                item -> java.util.Collections.singletonList(item.admissionFee()),
                ChatGroundingViolation.UNSUPPORTED_ADMISSION_FEE);
        validateClaims(text, evidence, PHONE,
                item -> java.util.Collections.singletonList(item.phoneNumber()),
                ChatGroundingViolation.UNSUPPORTED_PHONE_NUMBER);
    }

    private static void validateClaims(
            String text,
            List<ChatTouristSpotEvidence> evidence,
            Pattern pattern,
            Function<ChatTouristSpotEvidence, List<String>> values,
            ChatGroundingViolation violation
    ) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String claim = matcher.group();
            boolean supported = evidence.stream()
                    .flatMap(item -> values.apply(item).stream())
                    .filter(Objects::nonNull)
                    .anyMatch(value -> value.contains(claim));
            if (!supported) {
                throw new ChatAnswerGroundingException(violation);
            }
        }
    }
}
