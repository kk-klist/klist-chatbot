package com.klist.chatbot.speech.application;

import java.util.Objects;

public class SpeechToTextException extends RuntimeException {

    private final SpeechToTextFailureType failureType;

    public SpeechToTextException(SpeechToTextFailureType failureType, String message) {
        this(failureType, message, null);
    }

    public SpeechToTextException(
            SpeechToTextFailureType failureType,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.failureType = Objects.requireNonNull(failureType, "failureType must not be null");
    }

    public SpeechToTextFailureType failureType() {
        return failureType;
    }
}
