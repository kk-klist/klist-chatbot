package com.klist.chatbot.chat.application.answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klist.chatbot.chat.application.llm.LlmGenerationResult;
import org.junit.jupiter.api.Test;

class ChatLlmResponseParserTest {

    private final ChatLlmResponseParser parser = new ChatLlmResponseParser(new ObjectMapper());

    @Test
    void parsesStructuredAnswerAndRecommendations() {
        LlmGenerationResult result = result("""
                {
                  "answer": "서울 야경을 즐기기 좋은 두 곳입니다.",
                  "recommendations": [
                    {"touristSpotId": 1001, "reason": "도심 전망을 볼 수 있습니다."},
                    {"touristSpotId": 1002, "reason": "한강 야경과 산책을 즐길 수 있습니다."}
                  ]
                }
                """);

        ChatGeneratedAnswer answer = parser.parse(result);

        assertThat(answer.answer()).isEqualTo("서울 야경을 즐기기 좋은 두 곳입니다.");
        assertThat(answer.recommendations())
                .extracting(ChatRecommendation::touristSpotId)
                .containsExactly(1001L, 1002L);
        assertThat(answer.recommendations().get(0).reason())
                .isEqualTo("도심 전망을 볼 수 있습니다.");
    }

    @Test
    void allowsGroundedAnswerWithoutRecommendations() {
        ChatGeneratedAnswer answer = parser.parse(result("""
                {"answer":"조건에 맞는 관광지를 찾지 못했습니다.","recommendations":[]}
                """));

        assertThat(answer.recommendations()).isEmpty();
    }

    @Test
    void rejectsMalformedUnknownAndMissingFields() {
        assertInvalid("{broken");
        assertInvalid("{\"answer\":\"답변\",\"recommendations\":[],\"unknown\":true}");
        assertInvalid("{\"answer\":\"답변\"}");
        assertInvalid("{\"answer\":\" \",\"recommendations\":[]}");
    }

    @Test
    void rejectsInvalidAndDuplicateRecommendations() {
        assertInvalid("""
                {"answer":"답변","recommendations":[{"touristSpotId":0,"reason":"이유"}]}
                """);
        assertInvalid("""
                {"answer":"답변","recommendations":[
                  {"touristSpotId":1001,"reason":"첫 번째"},
                  {"touristSpotId":1001,"reason":"두 번째"}
                ]}
                """);
        assertInvalid("""
                {"answer":"답변","recommendations":[{"touristSpotId":1001,"reason":" "}]}
                """);
    }

    private void assertInvalid(String json) {
        assertThatThrownBy(() -> parser.parse(result(json)))
                .isInstanceOf(ChatLlmResponseParsingException.class)
                .hasMessage("LLM structured response is invalid");
    }

    private static LlmGenerationResult result(String outputText) {
        return new LlmGenerationResult(outputText, "test-model", 10, 5);
    }
}
