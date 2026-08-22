package com.klist.chatbot.infrastructure.tourapi.ingestion;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public class TourApiIngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(TourApiIngestionScheduler.class);

    private final TourApiIngestionService ingestionService;
    private final TourApiIngestionProperties ingestionProperties;
    private final TourApiIngestionLock distributedLock;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicReference<TourApiIngestionExecution> lastExecution = new AtomicReference<>();

    public TourApiIngestionScheduler(
            TourApiIngestionService ingestionService,
            TourApiIngestionProperties ingestionProperties
    ) {
        this(ingestionService, ingestionProperties, TourApiIngestionLock.LOCAL_ONLY);
    }

    public TourApiIngestionScheduler(
            TourApiIngestionService ingestionService,
            TourApiIngestionProperties ingestionProperties,
            TourApiIngestionLock distributedLock
    ) {
        this.ingestionService = ingestionService;
        this.ingestionProperties = ingestionProperties;
        this.distributedLock = distributedLock;
    }

    @Scheduled(
            cron = "${tour-api.ingestion.schedule.cron:0 0 3 * * *}",
            zone = "${tour-api.ingestion.schedule.zone:Asia/Seoul}"
    )
    public void runScheduledIngestion() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Scheduled TourAPI ingestion skipped because a previous execution is still running.");
            return;
        }

        Optional<TourApiIngestionLock.Lease> lease = distributedLock.tryAcquire();
        if (lease.isEmpty()) {
            running.set(false);
            log.warn("Scheduled TourAPI ingestion skipped because the distributed lock is unavailable.");
            return;
        }

        Instant startedAt = Instant.now();
        try (TourApiIngestionLock.Lease ignored = lease.orElseThrow()) {
            log.info("Scheduled TourAPI ingestion started.");
            TourApiIngestionSummary summary = ingestionService.ingestAll(
                    ingestionProperties.getPageSize(),
                    ingestionProperties.getMaxPages(),
                    ingestionProperties.getMaxItems()
            );
            lastExecution.set(new TourApiIngestionExecution(startedAt, Instant.now(), summary, null));
            log.info("Scheduled TourAPI ingestion completed. total={}, failed={}",
                    summary.total(), summary.failed());
        } catch (RuntimeException exception) {
            lastExecution.set(new TourApiIngestionExecution(
                    startedAt,
                    Instant.now(),
                    null,
                    exception.getClass().getSimpleName() + ": " + safeMessage(exception)
            ));
            log.error("Scheduled TourAPI ingestion failed. error={}",
                    exception.getClass().getSimpleName());
        } finally {
            running.set(false);
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public Optional<TourApiIngestionExecution> lastExecution() {
        return Optional.ofNullable(lastExecution.get());
    }

    private static String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null ? "no message" : exception.getMessage();
    }
}
