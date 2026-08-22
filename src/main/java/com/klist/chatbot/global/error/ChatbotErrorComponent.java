package com.klist.chatbot.global.error;

public enum ChatbotErrorComponent {
    CHAT("chat"),
    LLM("llm"),
    SEARCH("search"),
    INDEX("index");

    private final String metricTag;

    ChatbotErrorComponent(String metricTag) {
        this.metricTag = metricTag;
    }

    public String metricTag() {
        return metricTag;
    }
}
