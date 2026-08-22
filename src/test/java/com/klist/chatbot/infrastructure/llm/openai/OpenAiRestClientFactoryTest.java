package com.klist.chatbot.infrastructure.llm.openai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OpenAiRestClientFactoryTest {

    @Test
    void appliesRequestBudgetToConnectAndReadTimeouts() throws Exception {
        OpenAiLlmProperties properties = new OpenAiLlmProperties();
        properties.setConnectTimeout(Duration.ofSeconds(3));
        OpenAiRestClientFactory factory = new OpenAiRestClientFactory(properties);

        OpenAiRestClientFactory.NetworkTimeouts timeouts =
                factory.resolveTimeouts(Duration.ofMillis(800));
        assertThat(timeouts.connectTimeout())
                .isEqualTo(Duration.ofMillis(800));
        assertThat(timeouts.readTimeout())
                .isEqualTo(Duration.ofMillis(800));
    }

    @Test
    void keepsShorterConfiguredConnectTimeout() throws Exception {
        OpenAiLlmProperties properties = new OpenAiLlmProperties();
        properties.setConnectTimeout(Duration.ofMillis(300));
        OpenAiRestClientFactory factory = new OpenAiRestClientFactory(properties);

        OpenAiRestClientFactory.NetworkTimeouts timeouts =
                factory.resolveTimeouts(Duration.ofSeconds(2));

        assertThat(timeouts.connectTimeout())
                .isEqualTo(Duration.ofMillis(300));
        assertThat(timeouts.readTimeout())
                .isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void keepsShorterConfiguredResponseTimeout() {
        OpenAiLlmProperties properties = new OpenAiLlmProperties();
        properties.setResponseTimeout(Duration.ofMillis(500));
        OpenAiRestClientFactory factory = new OpenAiRestClientFactory(properties);

        OpenAiRestClientFactory.NetworkTimeouts timeouts =
                factory.resolveTimeouts(Duration.ofSeconds(2));

        assertThat(timeouts.readTimeout()).isEqualTo(Duration.ofMillis(500));
    }
}
