package com.klist.chatbot.chat.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.klist.chatbot.chat.application.ChatQueryTimeoutException;
import com.klist.chatbot.chat.application.ChatProcessingFailedException;
import com.klist.chatbot.chat.application.ChatProcessingUnavailableException;
import com.klist.chatbot.chat.application.ChatRequestIdConflictException;
import com.klist.chatbot.chat.application.ChatRequestInProgressException;
import com.klist.chatbot.chat.application.InternalChatQueryUseCase;
import com.klist.chatbot.chat.presentation.dto.ChatQueryStatus;
import com.klist.chatbot.chat.presentation.dto.ChatSourceResponse;
import com.klist.chatbot.chat.presentation.dto.ChatSourceType;
import com.klist.chatbot.chat.presentation.dto.InternalChatQueryResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalChatQueryController.class)
class InternalChatQueryControllerContractTest {

    private static final String TRACE_ID = "trace-backend-001";
    private static final String INTERNAL_API_KEY = "test-internal-api-key";
    private static final UUID REQUEST_ID = UUID.fromString(
            "a22c717d-5a3e-46b5-92fc-f41624b85887"
    );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InternalChatQueryUseCase chatQueryUseCase;

    @Test
    void returnsAnswerSourcesTraceIdAndStatus() throws Exception {
        when(chatQueryUseCase.query(any(), eq(TRACE_ID))).thenReturn(new InternalChatQueryResponse(
                REQUEST_ID,
                "아이와 방문하기 좋은 실내 관광지입니다.",
                List.of(new ChatSourceResponse(
                        126480L,
                        "관악산",
                        ChatSourceType.TOURIST_SPOT,
                        8.7,
                        "https://example.com/tourist-spots/126480"
                )),
                TRACE_ID,
                ChatQueryStatus.COMPLETED,
                243L
        ));

        mockMvc.perform(post("/internal/chat/query")
                        .header("X-Trace-Id", TRACE_ID)
                        .header(InternalApiKeyAuthenticationFilter.HEADER_NAME, INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "a22c717d-5a3e-46b5-92fc-f41624b85887",
                                  "sessionId": "session-001",
                                  "userId": "user-001",
                                  "message": "서울 실내 관광지를 추천해줘",
                                  "timeoutMs": 5000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", TRACE_ID))
                .andExpect(jsonPath("$.answer").value("아이와 방문하기 좋은 실내 관광지입니다."))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID.toString()))
                .andExpect(jsonPath("$.sources[0].touristSpotId").value(126480))
                .andExpect(jsonPath("$.sources[0].type").value("TOURIST_SPOT"))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.suggestions").isArray())
                .andExpect(jsonPath("$.processingTimeMs").value(243));
    }

    @ParameterizedTest
    @EnumSource(ChatQueryStatus.class)
    void returnsEveryNormalStatusAsHttp200WithAnswerAndSuggestions(
            ChatQueryStatus responseStatus
    ) throws Exception {
        when(chatQueryUseCase.query(any(), eq(TRACE_ID))).thenReturn(
                new InternalChatQueryResponse(
                        REQUEST_ID,
                        "사용자 안내 답변",
                        List.of(),
                        List.of("후속 질문 예시"),
                        TRACE_ID,
                        responseStatus,
                        12L
                )
        );

        mockMvc.perform(post("/internal/chat/query")
                        .header("X-Trace-Id", TRACE_ID)
                        .header(InternalApiKeyAuthenticationFilter.HEADER_NAME, INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(responseStatus.name()))
                .andExpect(jsonPath("$.answer").value("사용자 안내 답변"))
                .andExpect(jsonPath("$.suggestions[0]").value("후속 질문 예시"));
    }

    @Test
    void acceptsClientRequestWithOnlySessionIdAndMessage() throws Exception {
        when(chatQueryUseCase.query(any(), eq(TRACE_ID))).thenAnswer(invocation -> {
            var request = invocation.getArgument(
                    0,
                    com.klist.chatbot.chat.presentation.dto.InternalChatQueryRequest.class
            );
            return new InternalChatQueryResponse(
                    request.requestId(), "추천 결과", List.of(), TRACE_ID,
                    ChatQueryStatus.COMPLETED, 10L
            );
        });

        mockMvc.perform(post("/internal/chat/query")
                        .header("X-Trace-Id", TRACE_ID)
                        .header(InternalApiKeyAuthenticationFilter.HEADER_NAME, INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "3b086657-4887-49da-938e-23b1e0efd2b8",
                                  "message": "서울에서 방문할 만한 관광지를 추천해줘"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void returnsValidationErrorWithTraceId() throws Exception {
        mockMvc.perform(post("/internal/chat/query")
                        .header("X-Trace-Id", TRACE_ID)
                        .header(InternalApiKeyAuthenticationFilter.HEADER_NAME, INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "a22c717d-5a3e-46b5-92fc-f41624b85887",
                                  "sessionId": "",
                                  "userId": "user-001",
                                  "message": "",
                                  "timeoutMs": 50
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Trace-Id", TRACE_ID))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                .andExpect(jsonPath("$.errors[*].field")
                        .value(org.hamcrest.Matchers.hasItems("sessionId", "message", "timeoutMs")));
    }

    @Test
    void returnsGatewayTimeoutUsingSameTraceId() throws Exception {
        when(chatQueryUseCase.query(any(), eq(TRACE_ID)))
                .thenThrow(new ChatQueryTimeoutException("simulated timeout"));

        mockMvc.perform(post("/internal/chat/query")
                        .header("X-Trace-Id", TRACE_ID)
                        .header(InternalApiKeyAuthenticationFilter.HEADER_NAME, INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "a22c717d-5a3e-46b5-92fc-f41624b85887",
                                  "sessionId": "session-001",
                                  "userId": "user-001",
                                  "message": "서울 관광지를 추천해줘"
                                }
                                """))
                .andExpect(status().isGatewayTimeout())
                .andExpect(header().string("X-Trace-Id", TRACE_ID))
                .andExpect(jsonPath("$.code").value("CHAT_QUERY_TIMEOUT"))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void returnsSafeProcessingFailedErrorUsingSameTraceId() throws Exception {
        when(chatQueryUseCase.query(any(), eq(TRACE_ID)))
                .thenThrow(new ChatProcessingFailedException(
                        "sensitive provider response",
                        new RuntimeException("raw model output")
                ));

        mockMvc.perform(post("/internal/chat/query")
                        .header("X-Trace-Id", TRACE_ID)
                        .header(InternalApiKeyAuthenticationFilter.HEADER_NAME, INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("X-Trace-Id", TRACE_ID))
                .andExpect(jsonPath("$.code").value("CHAT_PROCESSING_FAILED"))
                .andExpect(jsonPath("$.message").value(
                        "The chatbot response could not be validated."
                ))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("sensitive provider response")
                        )));
    }

    @Test
    void returnsServiceUnavailableForLlmAvailabilityFailure() throws Exception {
        when(chatQueryUseCase.query(any(), eq(TRACE_ID)))
                .thenThrow(new ChatProcessingUnavailableException("provider unavailable"));

        mockMvc.perform(post("/internal/chat/query")
                        .header("X-Trace-Id", TRACE_ID)
                        .header(InternalApiKeyAuthenticationFilter.HEADER_NAME, INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CHAT_PROCESSING_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Chat processing is not available."));
    }

    @Test
    void returnsConflictAndRetryAfterWhenSameRequestIsProcessing() throws Exception {
        when(chatQueryUseCase.query(any(), eq(TRACE_ID)))
                .thenThrow(new ChatRequestInProgressException());

        mockMvc.perform(post("/internal/chat/query")
                        .header("X-Trace-Id", TRACE_ID)
                        .header(InternalApiKeyAuthenticationFilter.HEADER_NAME, INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isConflict())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.code").value("REQUEST_IN_PROGRESS"))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));
    }

    @Test
    void returnsConflictWhenRequestIdHasDifferentContent() throws Exception {
        when(chatQueryUseCase.query(any(), eq(TRACE_ID)))
                .thenThrow(new ChatRequestIdConflictException());

        mockMvc.perform(post("/internal/chat/query")
                        .header("X-Trace-Id", TRACE_ID)
                        .header(InternalApiKeyAuthenticationFilter.HEADER_NAME, INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_ID_CONFLICT"))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));
    }

    @Test
    void generatesTraceIdWhenBackendDoesNotProvideOne() throws Exception {
        when(chatQueryUseCase.query(any(), any())).thenAnswer(invocation -> {
            String generatedTraceId = invocation.getArgument(1);
            return new InternalChatQueryResponse(
                    REQUEST_ID,
                    "검색 결과가 없습니다.",
                    List.of(),
                    generatedTraceId,
                    ChatQueryStatus.NO_RESULT,
                    10L
            );
        });

        mockMvc.perform(post("/internal/chat/query")
                        .header(InternalApiKeyAuthenticationFilter.HEADER_NAME, INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "a22c717d-5a3e-46b5-92fc-f41624b85887",
                                  "sessionId": "session-001",
                                  "userId": "user-001",
                                  "message": "없는 관광지를 찾아줘"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("NO_RESULT"))
                .andExpect(jsonPath("$.sources").isEmpty());
    }

    private static String validRequest() {
        return """
                {
                  "requestId": "a22c717d-5a3e-46b5-92fc-f41624b85887",
                  "sessionId": "session-001",
                  "userId": "user-001",
                  "message": "서울 관광지를 추천해줘",
                  "timeoutMs": 5000
                }
                """;
    }
}
