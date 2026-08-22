package com.klist.chatbot.infrastructure.search.query;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "search.tourist-spots.cache")
public class TouristSpotSearchCacheProperties {

    private boolean enabled;
    private Duration ttl = Duration.ofMinutes(5);
    private String keyPrefix = "klist:search:tourist-spots:";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }
}
