package com.klist.chatbot.infrastructure.llm.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klist.chatbot.chat.application.llm.LlmClientException;
import com.klist.chatbot.chat.application.llm.LlmFailureType;
import com.klist.chatbot.chat.application.llm.LlmGenerationRequest;
import com.klist.chatbot.chat.application.llm.LlmGenerationResult;
import com.klist.chatbot.chat.application.prompt.ChatPrompt;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class OpenAiResponsesClientTest {

    private static final String BASE_URL = "https://api.openai.test/v1";

    private OpenAiLlmProperties properties;
    private MockRestServiceServer server;
    private OpenAiResponsesClient client;
    private AtomicReference<Duration> requestedTimeout;

    @BeforeEach
    void setUp() {
        properties = enabledProperties();
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        requestedTimeout = new AtomicReference<>();
        client = new OpenAiResponsesClient(timeout -> {
            requestedTimeout.set(timeout);
            return restClient;
        }, new ObjectMapper(), properties);
    }

    @Test
    void sendsResponsesApiRequestAndMapsTextAndUsage() {
        server.expect(requestTo(BASE_URL + "/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-api-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "model": "gpt-5.6-sol",
                          "instructions": "근거만 사용하세요.",
                          "input": "서울 야경 명소를 추천해줘",
                          "reasoning": {"effort": "low"},
                          "max_output_tokens": 1200,
                          "store": false
                        }
                        """))
                .andExpect(jsonPath("$.text.format.type").value("json_schema"))
                .andExpect(jsonPath("$.text.format.name").value("tourist_chat_answer"))
                .andExpect(jsonPath("$.text.format.strict").value(true))
                .andExpect(jsonPath("$.text.format.schema.additionalProperties").value(false))
                .andExpect(jsonPath("$.text.format.schema.required[0]").value("answer"))
                .andExpect(jsonPath("$.text.format.schema.required[1]").value("recommendations"))
                .andExpect(jsonPath("$.text.format.schema.properties.recommendations.items"
                        + ".properties.touristSpotId.minimum").value(1))
                .andRespond(withSuccess("""
                        {
                          "id": "resp_123",
                          "model": "gpt-5.6-sol",
                          "output": [
                            {"type":"reasoning","content":[]},
                            {"type":"message","content":[
                              {"type":"output_text","text":"서울 전망대를 추천합니다."}
                            ]}
                          ],
                          "usage": {"input_tokens":120,"output_tokens":30,"total_tokens":150}
                        }
                        """, MediaType.APPLICATION_JSON));

        LlmGenerationResult result = client.generate(request());

        assertThat(result.outputText()).isEqualTo("서울 전망대를 추천합니다.");
        assertThat(result.model()).isEqualTo("gpt-5.6-sol");
        assertThat(result.inputTokens()).isEqualTo(120);
        assertThat(result.outputTokens()).isEqualTo(30);
        assertThat(requestedTimeout.get()).isEqualTo(Duration.ofSeconds(5));
        server.verify();
    }

    @ParameterizedTest
    @MethodSource("httpFailures")
    void classifiesHttpFailures(
            HttpStatus status,
            String providerCode,
            LlmFailureType expectedType,
            boolean retryable
    ) {
        server.expect(requestTo(BASE_URL + "/responses"))
                .andRespond(withStatus(status)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"" + providerCode + "\"}}"));

        assertThatThrownBy(() -> client.generate(request()))
                .isInstanceOfSatisfying(LlmClientException.class, exception -> {
                    assertThat(exception.failureType()).isEqualTo(expectedType);
                    assertThat(exception.httpStatus()).isEqualTo(status.value());
                    assertThat(exception.providerCode()).isEqualTo(providerCode);
                    assertThat(exception.retryable()).isEqualTo(retryable);
                    assertThat(exception.getMessage()).doesNotContain("test-api-key");
                });
        server.verify();
    }

    @Test
    void classifiesTimeoutWithoutSendingCredentialsInError() {
        RestClient timeoutClient = RestClient.builder()
                .requestFactory((uri, method) -> {
                    throw new SocketTimeoutException("simulated timeout");
                })
                .build();
        client = new OpenAiResponsesClient(timeout -> timeoutClient, new ObjectMapper(), properties);

        assertThatThrownBy(() -> client.generate(request()))
                .isInstanceOfSatisfying(LlmClientException.class, exception -> {
                    assertThat(exception.failureType()).isEqualTo(LlmFailureType.TIMEOUT);
                    assertThat(exception.retryable()).isTrue();
                    assertThat(exception.getMessage()).doesNotContain("test-api-key");
                });
    }

    @Test
    void classifiesTimeoutWrappedByResponseExtractionException() {
        RestClient timeoutClient = RestClient.builder()
                .requestFactory((uri, method) -> {
                    throw new RestClientException(
                            "Error while extracting response for type [java.lang.String]",
                            new SocketTimeoutException("Read timed out")
                    );
                })
                .build();
        client = new OpenAiResponsesClient(timeout -> timeoutClient, new ObjectMapper(), properties);

        assertThatThrownBy(() -> client.generate(request()))
                .isInstanceOfSatisfying(LlmClientException.class, exception -> {
                    assertThat(exception.failureType()).isEqualTo(LlmFailureType.TIMEOUT);
                    assertThat(exception.retryable()).isTrue();
                    assertThat(exception.getCause()).isInstanceOf(RestClientException.class);
                });
    }

    @Test
    void rejectsInvalidResponseAndDisabledConfiguration() {
        server.expect(requestTo(BASE_URL + "/responses"))
                .andRespond(withSuccess("{\"model\":\"gpt-5.6-sol\",\"output\":[],\"usage\":{"
                        + "\"input_tokens\":1,\"output_tokens\":0}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generate(request()))
                .isInstanceOfSatisfying(LlmClientException.class, exception ->
                        assertThat(exception.failureType()).isEqualTo(LlmFailureType.INVALID_RESPONSE));
        server.verify();

        properties.setEnabled(false);
        assertThatThrownBy(() -> client.generate(request()))
                .isInstanceOfSatisfying(LlmClientException.class, exception -> {
                    assertThat(exception.failureType()).isEqualTo(LlmFailureType.CONFIGURATION);
                    assertThat(exception.retryable()).isFalse();
                });
    }

    private static Stream<Arguments> httpFailures() {
        return Stream.of(
                Arguments.of(HttpStatus.UNAUTHORIZED, "invalid_api_key",
                        LlmFailureType.AUTHENTICATION, false),
                Arguments.of(HttpStatus.TOO_MANY_REQUESTS, "rate_limit_exceeded",
                        LlmFailureType.RATE_LIMIT, true),
                Arguments.of(HttpStatus.BAD_REQUEST, "model_not_found",
                        LlmFailureType.MODEL, false),
                Arguments.of(HttpStatus.INTERNAL_SERVER_ERROR, "server_error",
                        LlmFailureType.HTTP, true)
        );
    }

    private static LlmGenerationRequest request() {
        return new LlmGenerationRequest(
                new ChatPrompt("근거만 사용하세요.", "서울 야경 명소를 추천해줘"),
                Duration.ofSeconds(5)
        );
    }

    private static OpenAiLlmProperties enabledProperties() {
        OpenAiLlmProperties properties = new OpenAiLlmProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-api-key");
        return properties;
    }
}
