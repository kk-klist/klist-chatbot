package com.klist.chatbot.speech.application;

import java.util.Locale;
import java.util.Objects;

public record SpeechAudio(
        String filename,
        String contentType,
        byte[] content
) {

    public SpeechAudio {
        filename = filename == null ? "" : filename.trim();
        contentType = normalizeContentType(contentType);
        content = Objects.requireNonNull(content, "content must not be null").clone();
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int parameterDelimiter = contentType.indexOf(';');
        String mediaType = parameterDelimiter < 0
                ? contentType
                : contentType.substring(0, parameterDelimiter);
        return mediaType.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    public long size() {
        return content.length;
    }
}
