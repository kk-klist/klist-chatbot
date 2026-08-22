package com.klist.chatbot.infrastructure.tourapi.ingestion;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tour-api.ingestion.schedule")
public class TourApiIngestionScheduleProperties {

    private boolean enabled;
    private String cron = "0 0 3 * * *";
    private String zone = "Asia/Seoul";
    private String lockKey = "klist:tourapi:ingestion:schedule";
    private Duration lockTtl = Duration.ofHours(2);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public String getLockKey() {
        return lockKey;
    }

    public void setLockKey(String lockKey) {
        this.lockKey = lockKey;
    }

    public Duration getLockTtl() {
        return lockTtl;
    }

    public void setLockTtl(Duration lockTtl) {
        this.lockTtl = lockTtl;
    }
}
