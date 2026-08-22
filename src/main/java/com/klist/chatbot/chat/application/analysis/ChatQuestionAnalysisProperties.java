package com.klist.chatbot.chat.application.analysis;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chat.question-analysis")
public class ChatQuestionAnalysisProperties {

    private int resultSize = 5;
    private Float minimumScore = 0.1f;

    public int getResultSize() {
        return resultSize;
    }

    public void setResultSize(int resultSize) {
        this.resultSize = resultSize;
    }

    public Float getMinimumScore() {
        return minimumScore;
    }

    public void setMinimumScore(Float minimumScore) {
        this.minimumScore = minimumScore;
    }

    public void validate() {
        if (resultSize <= 0 || resultSize > 50) {
            throw new IllegalStateException("Chat question analysis result size must be between 1 and 50.");
        }
        if (minimumScore != null && minimumScore < 0) {
            throw new IllegalStateException("Chat question analysis minimum score must not be negative.");
        }
    }
}
