package com.klist.chatbot.global.error;

public enum ChatbotErrorType {
    TIMEOUT("timeout"),
    UNAVAILABLE("unavailable"),
    INVALID_RESPONSE("invalid_response"),
    SEARCH_FAILURE("search_failure"),
    INDEX_FAILURE("index_failure"),
    EXTERNAL_SERVICE_FAILURE("external_service_failure");

    private final String metricTag;

    ChatbotErrorType(String metricTag) {
        this.metricTag = metricTag;
    }

    public String metricTag() {
        return metricTag;
    }
}
