package com.klist.chatbot.infrastructure.llm.openai;

import java.time.Duration;
import java.util.Objects;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

final class OpenAiRestClientFactory {

    private final OpenAiLlmProperties properties;

    OpenAiRestClientFactory(OpenAiLlmProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    RestClient create(Duration timeout) {
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl().toString())
                .requestFactory(createRequestFactory(timeout))
                .build();
    }

    SimpleClientHttpRequestFactory createRequestFactory(Duration timeout) {
        NetworkTimeouts timeouts = resolveTimeouts(timeout);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeouts.connectTimeout());
        requestFactory.setReadTimeout(timeouts.readTimeout());
        return requestFactory;
    }

    NetworkTimeouts resolveTimeouts(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout must not be null");
        return new NetworkTimeouts(
                shorter(properties.getConnectTimeout(), timeout),
                shorter(properties.getResponseTimeout(), timeout)
        );
    }

    private static Duration shorter(Duration configuredTimeout, Duration requestTimeout) {
        return configuredTimeout.compareTo(requestTimeout) <= 0
                ? configuredTimeout
                : requestTimeout;
    }

    record NetworkTimeouts(Duration connectTimeout, Duration readTimeout) {
    }
}
