package com.klist.chatbot.infrastructure.search.failure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public class TouristSpotIndexFailureRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(
            TouristSpotIndexFailureRetryScheduler.class
    );

    private final TouristSpotIndexFailureRetryService retryService;
    private final TouristSpotIndexRetryProperties properties;

    public TouristSpotIndexFailureRetryScheduler(
            TouristSpotIndexFailureRetryService retryService,
            TouristSpotIndexRetryProperties properties
    ) {
        this.retryService = retryService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${search.tourist-spots.failure-retry.interval:30s}")
    public void retry() {
        if (!properties.isEnabled()) {
            return;
        }
        TouristSpotIndexRetrySummary summary = retryService.retryDue();
        if (summary.attempted() > 0) {
            log.info("Tourist spot index failure retry completed. summary={}", summary);
        }
    }
}
