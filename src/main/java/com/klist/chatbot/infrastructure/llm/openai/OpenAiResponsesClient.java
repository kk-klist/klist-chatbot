package com.klist.chatbot.infrastructure.llm.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.klist.chatbot.chat.application.llm.LlmClient;
import com.klist.chatbot.chat.application.llm.LlmClientException;
import com.klist.chatbot.chat.application.llm.LlmFailureType;
import com.klist.chatbot.chat.application.llm.LlmGenerationRequest;
import com.klist.chatbot.chat.application.llm.LlmGenerationResult;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class OpenAiResponsesClient implements LlmClient {

    private static final Map<String, Object> CHAT_ANSWER_SCHEMA = Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", java.util.List.of("answer", "recommendations"),
            "properties", Map.of(
                    "answer", Map.of("type", "string", "minLength", 1),
                    "recommendations", Map.of(
                            "type", "array",
                            "maxItems", 20,
                            "items", Map.of(
                                    "type", "object",
                                    "additionalProperties", false,
                                    "required", java.util.List.of("touristSpotId", "reason"),
                                    "properties", Map.of(
                                            "touristSpotId", Map.of("type", "integer", "minimum", 1),
                                            "reason", Map.of("type", "string", "minLength", 1)
                                    )
                            )
                    )
            )
    );

    private final Function<java.time.Duration, RestClient> restClientFactory;
    private final ObjectMapper objectMapper;
    private final OpenAiLlmProperties properties;

    public OpenAiResponsesClient(
            Function<java.time.Duration, RestClient> restClientFactory,
            ObjectMapper objectMapper,
            OpenAiLlmProperties properties
    ) {
        this.restClientFactory = Objects.requireNonNull(
                restClientFactory,
                "restClientFactory must not be null"
        );
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public LlmGenerationResult generate(LlmGenerationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        validateConfiguration();
        try {
            String responseBody = restClientFactory.apply(request.timeout()).post()
                    .uri("/responses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(properties.getApiKey()))
                    .body(requestBody(request))
                    .retrieve()
                    .body(String.class);
            return parseResponse(responseBody);
        } catch (HttpStatusCodeException exception) {
            throw toHttpException(exception);
        } catch (ResourceAccessException exception) {
            throw toResourceException(exception);
        } catch (RestClientException exception) {
            throw toRestClientException(exception);
        }
    }

    private Map<String, Object> requestBody(LlmGenerationRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("instructions", request.prompt().systemMessage());
        body.put("input", request.prompt().userMessage());
        body.put("reasoning", Map.of("effort", properties.getReasoningEffort()));
        body.put("text", Map.of("format", Map.of(
                "type", "json_schema",
                "name", "tourist_chat_answer",
                "strict", true,
                "schema", CHAT_ANSWER_SCHEMA
        )));
        body.put("max_output_tokens", properties.getMaxOutputTokens());
        body.put("store", false);
        return body;
    }

    private LlmGenerationResult parseResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw invalidResponse("OpenAI response body is empty", null);
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String outputText = findOutputText(root.path("output"));
            String model = text(root.get("model"));
            JsonNode usage = root.path("usage");
            long inputTokens = nonNegativeLong(usage.get("input_tokens"), "input_tokens");
            long outputTokens = nonNegativeLong(usage.get("output_tokens"), "output_tokens");
            if (outputText == null || outputText.isBlank() || model == null || model.isBlank()) {
                throw invalidResponse("OpenAI response is missing output text or model", null);
            }
            return new LlmGenerationResult(outputText, model, inputTokens, outputTokens);
        } catch (JsonProcessingException exception) {
            throw invalidResponse("OpenAI response JSON is invalid", exception);
        }
    }

    private static String findOutputText(JsonNode output) {
        if (!output.isArray()) {
            return null;
        }
        StringBuilder combined = new StringBuilder();
        for (JsonNode item : output) {
            JsonNode content = item.path("content");
            if (!content.isArray()) {
                continue;
            }
            for (JsonNode part : content) {
                if ("output_text".equals(text(part.get("type")))) {
                    String value = text(part.get("text"));
                    if (value != null && !value.isBlank()) {
                        if (!combined.isEmpty()) {
                            combined.append('\n');
                        }
                        combined.append(value);
                    }
                }
            }
        }
        return combined.isEmpty() ? null : combined.toString();
    }

    private void validateConfiguration() {
        try {
            properties.validate();
        } catch (IllegalStateException exception) {
            throw new LlmClientException(
                    LlmFailureType.CONFIGURATION,
                    "OpenAI client configuration is invalid",
                    null,
                    null,
                    false,
                    exception
            );
        }
        if (!properties.isEnabled()) {
            throw new LlmClientException(
                    LlmFailureType.CONFIGURATION,
                    "OpenAI client is disabled",
                    null,
                    null,
                    false,
                    null
            );
        }
    }

    private LlmClientException toHttpException(HttpStatusCodeException exception) {
        int status = exception.getStatusCode().value();
        String providerCode = errorCode(exception.getResponseBodyAsString());
        LlmFailureType type;
        boolean retryable = false;
        if (status == 401 || status == 403) {
            type = LlmFailureType.AUTHENTICATION;
        } else if (status == 429) {
            type = LlmFailureType.RATE_LIMIT;
            retryable = true;
        } else if ("model_not_found".equals(providerCode) || "unsupported_model".equals(providerCode)) {
            type = LlmFailureType.MODEL;
        } else {
            type = LlmFailureType.HTTP;
            retryable = status >= 500;
        }
        return new LlmClientException(
                type,
                "OpenAI request failed with HTTP status " + status,
                status,
                providerCode,
                retryable,
                exception
        );
    }

    private LlmClientException toResourceException(ResourceAccessException exception) {
        if (hasCause(exception, SocketTimeoutException.class)
                || hasCause(exception, HttpTimeoutException.class)) {
            return new LlmClientException(
                    LlmFailureType.TIMEOUT,
                    "OpenAI request timed out",
                    null,
                    null,
                    true,
                    exception
            );
        }
        return new LlmClientException(
                LlmFailureType.CONNECTION,
                hasCause(exception, ConnectException.class)
                        ? "Unable to connect to OpenAI"
                        : "OpenAI connection failed",
                null,
                null,
                true,
                exception
        );
    }

    private LlmClientException toRestClientException(RestClientException exception) {
        if (hasCause(exception, SocketTimeoutException.class)
                || hasCause(exception, HttpTimeoutException.class)) {
            return new LlmClientException(
                    LlmFailureType.TIMEOUT,
                    "OpenAI response read timed out",
                    null,
                    null,
                    true,
                    exception
            );
        }
        if (hasCause(exception, ConnectException.class)) {
            return new LlmClientException(
                    LlmFailureType.CONNECTION,
                    "Unable to connect to OpenAI",
                    null,
                    null,
                    true,
                    exception
            );
        }
        return new LlmClientException(
                LlmFailureType.INVALID_RESPONSE,
                "OpenAI response could not be extracted",
                null,
                null,
                false,
                exception
        );
    }

    private String errorCode(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            return text(objectMapper.readTree(responseBody).path("error").get("code"));
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private static long nonNegativeLong(JsonNode node, String field) {
        if (node == null || !node.canConvertToLong() || node.longValue() < 0) {
            throw invalidResponse("OpenAI response has invalid " + field, null);
        }
        return node.longValue();
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static LlmClientException invalidResponse(String message, Throwable cause) {
        return new LlmClientException(
                LlmFailureType.INVALID_RESPONSE,
                message,
                null,
                null,
                false,
                cause
        );
    }
}
