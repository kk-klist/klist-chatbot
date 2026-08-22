package com.klist.chatbot.infrastructure.tourapi.client;

import java.time.Duration;

@FunctionalInterface
interface TourApiRetrySleeper {

    void sleep(Duration duration) throws InterruptedException;
}
