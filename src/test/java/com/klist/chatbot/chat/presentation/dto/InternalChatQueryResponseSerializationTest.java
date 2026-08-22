package com.klist.chatbot.chat.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class InternalChatQueryResponseSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest
    @EnumSource(ChatQueryStatus.class)
    void roundTripsAnswerAndSuggestionsForEveryStatus(ChatQueryStatus status) throws Exception {
        InternalChatQueryResponse original = new InternalChatQueryResponse(
                UUID.fromString("a22c717d-5a3e-46b5-92fc-f41624b85887"),
                "응답 내용",
                List.of(),
                List.of("후속 질문 1", "후속 질문 2"),
                "trace-001",
                status,
                10L
        );

        String json = objectMapper.writeValueAsString(original);
        InternalChatQueryResponse restored = objectMapper.readValue(
                json, InternalChatQueryResponse.class
        );

        assertThat(restored).isEqualTo(original);
        assertThat(json).contains("\"suggestions\"");
    }
}
