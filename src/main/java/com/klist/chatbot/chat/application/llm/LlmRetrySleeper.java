package com.klist.chatbot.chat.application.llm;

import java.time.Duration;

@FunctionalInterface
interface LlmRetrySleeper {

    void sleep(Duration duration) throws InterruptedException;
}
