package com.klist.chatbot.chat.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import com.klist.chatbot.chat.application.llm.LlmClient;
import com.klist.chatbot.chat.application.llm.LlmClientException;
import com.klist.chatbot.chat.application.llm.LlmFailureType;
import com.klist.chatbot.chat.application.llm.LlmGenerationResult;
import com.klist.chatbot.search.application.TouristSpotSearchEvidence;
import com.klist.chatbot.search.application.TouristSpotSearchGateway;
import com.klist.chatbot.search.application.TouristSpotSearchResult;
import com.klist.chatbot.infrastructure.search.query.TouristSpotSearchException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "llm.openai.enabled=true",
        "llm.openai.api-key=test-api-key"
})
@AutoConfigureMockMvc
class InternalChatQueryIntegrationTest {

    private static final String TRACE_ID = "integration-trace-001";
    private static final String INTERNAL_API_KEY = "test-internal-api-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TouristSpotSearchGateway searchGateway;

    @MockitoBean
    private LlmClient llmClient;

    @Test
    void completesHttpRequestThroughSearchPromptLlmParsingAndGrounding() throws Exception {
        when(searchGateway.search(any(), any(Duration.class)))
                .thenReturn(searchResult(evidence()));
        when(llmClient.generate(any())).thenReturn(new LlmGenerationResult(
                """
                {
                  "answer": "서울 야경을 보려면 남산서울타워를 추천합니다.",
                  "recommendations": [
                    {"touristSpotId": 1001, "reason": "서울 전경을 볼 수 있습니다."}
                  ]
                }
                """,
                "test-model",
                120,
                30,
                Duration.ofMillis(200)
        ));

        mockMvc.perform(post("/internal/chat/query")
                        .header("X-Trace-Id", TRACE_ID)
                        .header(InternalApiKeyAuthenticationFilter.HEADER_NAME, INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("서울 야경 명소를 추천해줘")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", TRACE_ID))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                .andExpect(jsonPath("$.answer").value(
                        "서울 야경을 보려면 남산서울타워를 추천합니다."
                ))
                .andExpect(jsonPath("$.sources[0].touristSpotId").value(1001))
                .andExpect(jsonPath("$.sources[0].title").value("남산서울타워"))
                .andExpect(jsonPath("$.sources[0].score").value(8.5));
    }

    @Test
    void returnsNoResultWithoutCallingLlmWhenSearchHasNoEvidence() throws Exception {
        when(searchGateway.search(any(), any(Duration.class))).thenReturn(searchResult());

        mockMvc.perform(post("/internal/chat/query")
                        .header("X-Trace-Id", TRACE_ID)
                        .header(InternalApiKeyAuthenticationFilter.HEADER_NAME, INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("없는 관광지를 찾아줘")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_RESULT"))
                .andExpect(jsonPath("$.sources").isEmpty())
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));

        verifyNoInteractions(llmClient);
    }

    @Test
    void translatesLlmTimeoutToBackendErrorContract() throws Exception {
        when(searchGateway.search(any(), any(Duration.class)))
                .thenReturn(searchResult(evidence()));
        when(llmClient.generate(any())).thenThrow(new LlmClientException(
                LlmFailureType.TIMEOUT,
                "provider timeout",
                null,
                null,
                true,
                null
        ));

        mockMvc.perform(post("/internal/chat/query")
                        .header("X-Trace-Id", TRACE_ID)
                        .header(InternalApiKeyAuthenticationFilter.HEADER_NAME, INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("서울 야경 명소를 추천해줘")))
                .andExpect(status().isGatewayTimeout())
                .andExpect(header().string("X-Trace-Id", TRACE_ID))
                .andExpect(jsonPath("$.code").value("CHAT_QUERY_TIMEOUT"))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));
    }

    @Test
    void returnsSafeBackendErrorWithoutSecretsWhenElasticsearchIsUnavailable() throws Exception {
        when(searchGateway.search(any(), any(Duration.class))).thenThrow(new TouristSpotSearchException(
                "Elasticsearch connection failed: secret-token",
                new RuntimeException("http://elastic-user:elastic-password@elasticsearch:9200"),
                true
        ));

        mockMvc.perform(post("/internal/chat/query")
                        .header("X-Trace-Id", TRACE_ID)
                        .header(InternalApiKeyAuthenticationFilter.HEADER_NAME, INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("서울 야경 명소를 추천해줘")))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("X-Trace-Id", TRACE_ID))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value(
                        "The chatbot request could not be processed."
                ))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(INTERNAL_API_KEY)
                )))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret-token")
                )))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("elastic-password")
                )));
    }

    private static TouristSpotSearchResult searchResult(TouristSpotSearchEvidence... evidence) {
        return new TouristSpotSearchResult(
                List.of(evidence),
                evidence.length,
                Duration.ofMillis(15)
        );
    }

    private static TouristSpotSearchEvidence evidence() {
        return new TouristSpotSearchEvidence(
                1001L,
                "남산서울타워",
                "서울을 대표하는 전망 관광지",
                "서울특별시 용산구 남산공원길",
                11L,
                21L,
                12,
                37.5512,
                126.9882,
                "https://example.com/image.jpg",
                "02-0000-0000",
                "10:00~22:00",
                "유료",
                "https://example.com/reservation",
                8.5f
        );
    }

    private static String validRequest(String message) {
        return """
                {
                  "requestId": "a22c717d-5a3e-46b5-92fc-f41624b85887",
                  "sessionId": "session-001",
                  "userId": "user-001",
                  "message": "%s",
                  "timeoutMs": 5000
                }
                """.formatted(message);
    }
}
