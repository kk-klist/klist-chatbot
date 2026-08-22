package com.klist.chatbot.chat.application.answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.klist.chatbot.chat.application.evidence.ChatEvidenceContext;
import com.klist.chatbot.chat.application.evidence.ChatTouristSpotEvidence;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatGeneratedAnswerValidatorTest {

    private final ChatGeneratedAnswerValidator validator = new ChatGeneratedAnswerValidator();

    @Test
    void acceptsRecommendationsContainedInSearchEvidence() {
        ChatGeneratedAnswer answer = new ChatGeneratedAnswer(
                "검색된 두 관광지를 추천합니다.",
                List.of(
                        new ChatRecommendation(1002L, "한강 야경을 볼 수 있습니다."),
                        new ChatRecommendation(1001L, "도심 전망을 볼 수 있습니다.")
                )
        );

        ChatGeneratedAnswer validated = validator.validate(answer, evidenceContext(1001L, 1002L, 1003L));

        assertThat(validated).isSameAs(answer);
    }

    @Test
    void rejectsEveryRecommendationOutsideSearchEvidence() {
        ChatGeneratedAnswer answer = new ChatGeneratedAnswer(
                "관광지를 추천합니다.",
                List.of(
                        new ChatRecommendation(1001L, "검색 근거에 있습니다."),
                        new ChatRecommendation(9001L, "검색 근거에 없습니다."),
                        new ChatRecommendation(9002L, "검색 근거에 없습니다.")
                )
        );

        assertThatThrownBy(() -> validator.validate(answer, evidenceContext(1001L, 1002L)))
                .isInstanceOfSatisfying(ChatAnswerGroundingException.class, exception -> {
                    assertThat(exception.unsupportedTouristSpotIds()).containsExactly(9001L, 9002L);
                    assertThat(exception.getMessage()).doesNotContain("검색 근거에 없습니다");
                });
    }

    @Test
    void acceptsAnswerWithoutRecommendationsWhenEvidenceIsEmpty() {
        ChatGeneratedAnswer answer = new ChatGeneratedAnswer(
                "조건에 맞는 관광지를 찾지 못했습니다.",
                List.of()
        );

        ChatGeneratedAnswer validated = validator.validate(
                answer,
                new ChatEvidenceContext(List.of(), 0, Duration.ZERO)
        );

        assertThat(validated).isSameAs(answer);
    }

    @Test
    void requiresAnswerAndEvidenceContext() {
        ChatGeneratedAnswer answer = new ChatGeneratedAnswer("답변", List.of());

        assertThatNullPointerException()
                .isThrownBy(() -> validator.validate(null, evidenceContext(1001L)))
                .withMessage("generatedAnswer must not be null");
        assertThatNullPointerException()
                .isThrownBy(() -> validator.validate(answer, null))
                .withMessage("evidenceContext must not be null");
    }

    private static ChatEvidenceContext evidenceContext(Long... ids) {
        List<ChatTouristSpotEvidence> touristSpots = java.util.Arrays.stream(ids)
                .map(ChatGeneratedAnswerValidatorTest::evidence)
                .toList();
        return new ChatEvidenceContext(touristSpots, touristSpots.size(), Duration.ofMillis(5));
    }

    private static ChatTouristSpotEvidence evidence(Long id) {
        return new ChatTouristSpotEvidence(
                id,
                "관광지 " + id,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                1.0f
        );
    }
}
