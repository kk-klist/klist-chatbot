package com.klist.chatbot.speech.application;

public enum SpeechToTextFailureType {
    INVALID_FILE,
    FILE_TOO_LARGE,
    TIMEOUT,
    EMPTY_RESULT,
    PROVIDER_UNAVAILABLE,
    CONFIGURATION
}
