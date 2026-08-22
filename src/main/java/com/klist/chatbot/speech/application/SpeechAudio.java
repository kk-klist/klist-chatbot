package com.klist.chatbot.speech.application;

import java.util.Objects;

public record SpeechAudio(
        String filename,
        String contentType,
        byte[] content
) {

    public SpeechAudio {
        filename = filename == null ? "" : filename.trim();
        contentType = contentType == null ? "" : contentType.trim().toLowerCase();
        content = Objects.requireNonNull(content, "content must not be null").clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    public long size() {
        return content.length;
    }
}
