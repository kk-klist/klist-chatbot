package com.klist.chatbot.infrastructure.search.query;

import java.time.Duration;

@FunctionalInterface
interface SearchRetrySleeper {

    void sleep(Duration duration) throws InterruptedException;
}
