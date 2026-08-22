package com.klist.chatbot.infrastructure.llm.openai;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm.openai")
public class OpenAiLlmProperties {

    private static final Duration MAX_TIMEOUT = Duration.ofMinutes(2);

    private boolean enabled;
    private URI baseUrl = URI.create("https://api.openai.com/v1");
    private String apiKey;
    private String model = "gpt-5.6-sol";
    private String reasoningEffort = "low";
    private int maxOutputTokens = 1200;
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration responseTimeout = Duration.ofSeconds(20);
    private int retryMaxAttempts = 3;
    private Duration retryInitialBackoff = Duration.ofMillis(100);
    private Duration retryMaxBackoff = Duration.ofSeconds(1);

    @PostConstruct
    public void validate() {
        if (enabled && (apiKey == null || apiKey.isBlank())) {
            throw new IllegalStateException("OpenAI API key is required when LLM is enabled");
        }
        if (baseUrl == null || !isHttp(baseUrl)) {
            throw new IllegalStateException("OpenAI base URL must use http or https");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("OpenAI model must not be blank");
        }
        if (!isSupportedReasoningEffort(reasoningEffort)) {
            throw new IllegalStateException("Unsupported OpenAI reasoning effort: " + reasoningEffort);
        }
        if (maxOutputTokens <= 0) {
            throw new IllegalStateException("OpenAI max output tokens must be positive");
        }
        validateTimeout(connectTimeout, "connect timeout");
        validateTimeout(responseTimeout, "response timeout");
        if (retryMaxAttempts < 1 || retryMaxAttempts > 5) {
            throw new IllegalStateException("OpenAI retry max attempts must be between 1 and 5");
        }
        validateTimeout(retryInitialBackoff, "retry initial backoff");
        validateTimeout(retryMaxBackoff, "retry max backoff");
        if (retryInitialBackoff.compareTo(retryMaxBackoff) > 0) {
            throw new IllegalStateException(
                    "OpenAI retry initial backoff must not exceed max backoff"
            );
        }
    }

    private static boolean isHttp(URI uri) {
        return "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
    }

    private static boolean isSupportedReasoningEffort(String value) {
        return value != null && switch (value.trim().toLowerCase()) {
            case "none", "low", "medium", "high", "xhigh", "max" -> true;
            default -> false;
        };
    }

    private static void validateTimeout(Duration timeout, String name) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()
                || timeout.compareTo(MAX_TIMEOUT) > 0) {
            throw new IllegalStateException("OpenAI " + name + " must be between 1ms and 2m");
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

    public String getReasoningEffort() {
        return reasoningEffort;
    }

    public void setReasoningEffort(String reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getResponseTimeout() {
        return responseTimeout;
    }

    public void setResponseTimeout(Duration responseTimeout) {
        this.responseTimeout = responseTimeout;
    }

    public int getRetryMaxAttempts() {
        return retryMaxAttempts;
    }

    public void setRetryMaxAttempts(int retryMaxAttempts) {
        this.retryMaxAttempts = retryMaxAttempts;
    }

    public Duration getRetryInitialBackoff() {
        return retryInitialBackoff;
    }

    public void setRetryInitialBackoff(Duration retryInitialBackoff) {
        this.retryInitialBackoff = retryInitialBackoff;
    }

    public Duration getRetryMaxBackoff() {
        return retryMaxBackoff;
    }

    public void setRetryMaxBackoff(Duration retryMaxBackoff) {
        this.retryMaxBackoff = retryMaxBackoff;
    }
}
