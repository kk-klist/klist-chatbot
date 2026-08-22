package com.klist.chatbot.infrastructure.speech.openai;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stt.openai")
public class OpenAiSpeechToTextProperties {

    private boolean enabled;
    private URI baseUrl = URI.create("https://api.openai.com/v1");
    private String apiKey;
    private String model = "gpt-4o-mini-transcribe";
    private String language = "ko";
    private Duration connectTimeout = Duration.ofSeconds(3);

    @PostConstruct
    public void validate() {
        if (enabled && (apiKey == null || apiKey.isBlank())) {
            throw new IllegalStateException("OpenAI STT API key is required when STT is enabled");
        }
        if (baseUrl == null || !("http".equalsIgnoreCase(baseUrl.getScheme())
                || "https".equalsIgnoreCase(baseUrl.getScheme()))) {
            throw new IllegalStateException("OpenAI STT base URL must use http or https");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("OpenAI STT model must not be blank");
        }
        if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()) {
            throw new IllegalStateException("OpenAI STT connect timeout must be positive");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public URI getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(URI baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }
}
