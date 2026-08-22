package com.klist.chatbot.chat.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klist.chatbot.chat.application.InternalChatQueryUseCase;
import com.klist.chatbot.chat.presentation.dto.ChatQueryStatus;
import com.klist.chatbot.chat.presentation.dto.InternalChatQueryResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BackendChatContractHttpIntegrationTest {

    private static final String TRACE_ID = "backend-http-contract-001";
    private static final String INTERNAL_API_KEY = "test-internal-api-key";
    private static final UUID REQUEST_ID = UUID.fromString(
            "a22c717d-5a3e-46b5-92fc-f41624b85887"
    );

    @Value("${local.server.port}")
    private int port;

    @MockitoBean
    private InternalChatQueryUseCase chatQueryUseCase;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest
    @EnumSource(ChatQueryStatus.class)
    void backendClientDeserializesEveryNormalStatusFromActualHttpServer(
            ChatQueryStatus status
    ) throws Exception {
        when(chatQueryUseCase.query(any(), eq(TRACE_ID))).thenReturn(
                new InternalChatQueryResponse(
                        REQUEST_ID,
                        "사용자에게 전달할 답변",
                        List.of(),
                        List.of("추천 후속 질문"),
                        TRACE_ID,
                        status,
                        15L
                )
        );
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/internal/chat/query"))
                .header("Content-Type", "application/json")
                .header("X-Trace-Id", TRACE_ID)
                .header(InternalApiKeyAuthenticationFilter.HEADER_NAME, INTERNAL_API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {
                          "requestId": "a22c717d-5a3e-46b5-92fc-f41624b85887",
                          "sessionId": "session-001",
                          "userId": "user-001",
                          "message": "서울 관광지를 추천해 주세요"
                        }
                        """))
                .build();

        HttpResponse<String> httpResponse = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString()
        );
        InternalChatQueryResponse response = objectMapper.readValue(
                httpResponse.body(), InternalChatQueryResponse.class
        );

        assertThat(httpResponse.statusCode()).isEqualTo(200);
        assertThat(response.status()).isEqualTo(status);
        assertThat(response.answer()).isEqualTo("사용자에게 전달할 답변");
        assertThat(response.suggestions()).containsExactly("추천 후속 질문");
        assertThat(response.traceId()).isEqualTo(TRACE_ID);
    }
}
