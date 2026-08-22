package com.klist.chatbot.infrastructure.llm.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class OpenAiLlmPropertiesTest {

    @Test
    void providesSafeDisabledDefaultsAndCurrentModel() {
        OpenAiLlmProperties properties = new OpenAiLlmProperties();

        properties.validate();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getBaseUrl()).isEqualTo(URI.create("https://api.openai.com/v1"));
        assertThat(properties.getApiKey()).isNull();
        assertThat(properties.getModel()).isEqualTo("gpt-5.6-sol");
        assertThat(properties.getReasoningEffort()).isEqualTo("low");
        assertThat(properties.getMaxOutputTokens()).isEqualTo(1200);
        assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.getResponseTimeout()).isEqualTo(Duration.ofSeconds(20));
        assertThat(properties.getRetryMaxAttempts()).isEqualTo(3);
        assertThat(properties.getRetryInitialBackoff()).isEqualTo(Duration.ofMillis(100));
        assertThat(properties.getRetryMaxBackoff()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void acceptsExternalizedEnabledConfiguration() {
        OpenAiLlmProperties properties = new OpenAiLlmProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-api-key");
        properties.setModel("custom-model");
        properties.setReasoningEffort("medium");
        properties.setMaxOutputTokens(800);
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setResponseTimeout(Duration.ofSeconds(15));

        properties.validate();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getModel()).isEqualTo("custom-model");
    }

    @Test
    void rejectsEnabledConfigurationWithoutApiKey() {
        OpenAiLlmProperties properties = new OpenAiLlmProperties();
        properties.setEnabled(true);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API key");
    }

    @Test
    void rejectsInvalidEndpointReasoningAndLimits() {
        OpenAiLlmProperties properties = new OpenAiLlmProperties();
        properties.setBaseUrl(URI.create("file:///tmp/openai"));
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base URL");

        properties.setBaseUrl(URI.create("https://api.openai.com/v1"));
        properties.setReasoningEffort("extreme");
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reasoning effort");

        properties.setReasoningEffort("low");
        properties.setMaxOutputTokens(0);
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("max output tokens");
    }

    @Test
    void rejectsInvalidRetryConfiguration() {
        OpenAiLlmProperties properties = new OpenAiLlmProperties();
        properties.setRetryMaxAttempts(0);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("retry max attempts");

        properties.setRetryMaxAttempts(3);
        properties.setRetryInitialBackoff(Duration.ofSeconds(2));
        properties.setRetryMaxBackoff(Duration.ofSeconds(1));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("initial backoff");
    }
}
