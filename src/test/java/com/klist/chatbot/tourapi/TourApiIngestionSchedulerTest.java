package com.klist.chatbot.tourapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.klist.chatbot.domain.touristspot.service.result.TouristSpotImportSummary;
import com.klist.chatbot.infrastructure.tourapi.ingestion.TourApiIngestionLock;
import com.klist.chatbot.infrastructure.tourapi.ingestion.TourApiIngestionProperties;
import com.klist.chatbot.infrastructure.tourapi.ingestion.TourApiIngestionScheduler;
import com.klist.chatbot.infrastructure.tourapi.ingestion.TourApiIngestionService;
import com.klist.chatbot.infrastructure.tourapi.ingestion.TourApiIngestionSummary;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TourApiIngestionSchedulerTest {

    @Test
    void storesSuccessfulExecutionResult() {
        TourApiIngestionService service = mock(TourApiIngestionService.class);
        TourApiIngestionProperties properties = properties();
        when(service.ingestAll(50, 2, 20)).thenReturn(summary());
        TourApiIngestionScheduler scheduler = new TourApiIngestionScheduler(service, properties);

        scheduler.runScheduledIngestion();

        assertThat(scheduler.isRunning()).isFalse();
        assertThat(scheduler.lastExecution()).isPresent().get()
                .satisfies(execution -> {
                    assertThat(execution.isSuccess()).isTrue();
                    assertThat(execution.summary().total()).isZero();
                });
    }

    @Test
    void skipsOverlappingExecution() throws Exception {
        TourApiIngestionService service = mock(TourApiIngestionService.class);
        TourApiIngestionProperties properties = properties();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger invocations = new AtomicInteger();
        when(service.ingestAll(50, 2, 20)).thenAnswer(invocation -> {
            invocations.incrementAndGet();
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return summary();
        });
        TourApiIngestionScheduler scheduler = new TourApiIngestionScheduler(service, properties);

        Thread first = new Thread(scheduler::runScheduledIngestion);
        first.start();
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        scheduler.runScheduledIngestion();
        release.countDown();
        first.join(5_000);

        assertThat(invocations).hasValue(1);
        assertThat(scheduler.isRunning()).isFalse();
    }

    @Test
    void releasesLockAndStoresFailure() {
        TourApiIngestionService service = mock(TourApiIngestionService.class);
        TourApiIngestionProperties properties = properties();
        when(service.ingestAll(50, 2, 20)).thenThrow(new IllegalStateException("database unavailable"));
        TourApiIngestionScheduler scheduler = new TourApiIngestionScheduler(service, properties);

        scheduler.runScheduledIngestion();

        assertThat(scheduler.isRunning()).isFalse();
        assertThat(scheduler.lastExecution()).isPresent().get()
                .satisfies(execution -> {
                    assertThat(execution.isSuccess()).isFalse();
                    assertThat(execution.failure()).contains("IllegalStateException");
                });
    }

    @Test
    void skipsExecutionWhenDistributedLockIsHeld() {
        TourApiIngestionService service = mock(TourApiIngestionService.class);
        TourApiIngestionLock lock = () -> Optional.empty();
        TourApiIngestionScheduler scheduler = new TourApiIngestionScheduler(
                service, properties(), lock
        );

        scheduler.runScheduledIngestion();

        verify(service, never()).ingestAll(50, 2, 20);
        assertThat(scheduler.isRunning()).isFalse();
        assertThat(scheduler.lastExecution()).isEmpty();
    }

    @Test
    void releasesDistributedLockAfterExecution() {
        TourApiIngestionService service = mock(TourApiIngestionService.class);
        when(service.ingestAll(50, 2, 20)).thenReturn(summary());
        TourApiIngestionLock.Lease lease = mock(TourApiIngestionLock.Lease.class);
        TourApiIngestionLock lock = () -> Optional.of(lease);
        TourApiIngestionScheduler scheduler = new TourApiIngestionScheduler(
                service, properties(), lock
        );

        scheduler.runScheduledIngestion();

        verify(lease).close();
    }

    private static TourApiIngestionProperties properties() {
        TourApiIngestionProperties properties = new TourApiIngestionProperties();
        properties.setPageSize(50);
        properties.setMaxPages(2);
        properties.setMaxItems(20);
        return properties;
    }

    private static TourApiIngestionSummary summary() {
        Instant now = Instant.now();
        return new TourApiIngestionSummary(
                0, 0, 0, TouristSpotImportSummary.from(List.of()), List.of(), null, now, now
        );
    }
}
