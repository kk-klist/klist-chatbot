package com.klist.chatbot.infrastructure.tourapi.ingestion;

import java.util.Optional;

public interface TourApiIngestionLock {

    TourApiIngestionLock LOCAL_ONLY = () -> Optional.of(() -> { });

    Optional<Lease> tryAcquire();

    @FunctionalInterface
    interface Lease extends AutoCloseable {

        @Override
        void close();
    }
}
