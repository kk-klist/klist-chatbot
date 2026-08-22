package com.klist.chatbot.infrastructure.search.index;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties(prefix = "search.tourist-spots")
public class TouristSpotIndexProperties {

    private String alias = "tourist-spots";
    private String version = "v1";
    private Resource settingsLocation;
    private Resource mappingsLocation;
    private int retryMaxAttempts = 3;
    private Duration retryInitialBackoff = Duration.ofMillis(100);
    private Duration retryMaxBackoff = Duration.ofSeconds(1);

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Resource getSettingsLocation() {
        return settingsLocation;
    }

    public void setSettingsLocation(Resource settingsLocation) {
        this.settingsLocation = settingsLocation;
    }

    public Resource getMappingsLocation() {
        return mappingsLocation;
    }

    public void setMappingsLocation(Resource mappingsLocation) {
        this.mappingsLocation = mappingsLocation;
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

    public String versionedIndexName() {
        validateName(alias, "alias");
        validateName(version, "version");
        return alias + "-" + version;
    }

    public void validate() {
        versionedIndexName();
        if (settingsLocation == null || mappingsLocation == null) {
            throw new IllegalStateException("Index settings and mappings locations are required.");
        }
        if (retryMaxAttempts < 1 || retryMaxAttempts > 5) {
            throw new IllegalStateException("Search retry max attempts must be between 1 and 5.");
        }
        validateBackoff(retryInitialBackoff, "initial");
        validateBackoff(retryMaxBackoff, "max");
        if (retryInitialBackoff.compareTo(retryMaxBackoff) > 0) {
            throw new IllegalStateException("Search retry initial backoff must not exceed max backoff.");
        }
    }

    private void validateName(String value, String field) {
        if (value == null || !value.matches("[a-z0-9][a-z0-9._-]*")) {
            throw new IllegalStateException(field + " must be a valid lowercase Elasticsearch name.");
        }
    }

    private void validateBackoff(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()
                || value.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalStateException(
                    "Search retry " + field + " backoff must be between 1ms and 30s."
            );
        }
    }
}
