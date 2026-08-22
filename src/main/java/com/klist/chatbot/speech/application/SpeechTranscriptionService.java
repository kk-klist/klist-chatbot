package com.klist.chatbot.speech.application;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SpeechTranscriptionService {

    private static final Map<String, Set<String>> SUPPORTED_TYPES = Map.of(
            "mp3", Set.of("audio/mpeg", "audio/mp3"),
            "mp4", Set.of("audio/mp4", "video/mp4"),
            "mpeg", Set.of("audio/mpeg", "video/mpeg"),
            "mpga", Set.of("audio/mpeg"),
            "m4a", Set.of("audio/mp4", "audio/x-m4a"),
            "wav", Set.of("audio/wav", "audio/wave", "audio/x-wav"),
            "webm", Set.of("audio/webm", "video/webm")
    );

    private final SpeechToTextClient client;
    private final SpeechTranscriptionProperties properties;

    public SpeechTranscriptionService(
            SpeechToTextClient client,
            SpeechTranscriptionProperties properties
    ) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        properties.validate();
    }

    public String transcribe(SpeechAudio audio) {
        validate(audio);
        String text = client.transcribe(audio, properties.getTimeout());
        if (text == null || text.isBlank()) {
            throw new SpeechToTextException(
                    SpeechToTextFailureType.EMPTY_RESULT,
                    "Speech transcription result is empty"
            );
        }
        return text.trim();
    }

    private void validate(SpeechAudio audio) {
        Objects.requireNonNull(audio, "audio must not be null");
        if (audio.size() == 0) {
            throw invalidFile("Audio file must not be empty");
        }
        if (audio.size() > properties.getMaxFileSize()) {
            throw new SpeechToTextException(
                    SpeechToTextFailureType.FILE_TOO_LARGE,
                    "Audio file exceeds the configured size limit"
            );
        }
        String extension = extension(audio.filename());
        Set<String> contentTypes = SUPPORTED_TYPES.get(extension);
        if (contentTypes == null || !contentTypes.contains(audio.contentType())) {
            throw invalidFile("Unsupported audio file format or content type");
        }
    }

    private static String extension(String filename) {
        int delimiter = filename.lastIndexOf('.');
        return delimiter < 0 || delimiter == filename.length() - 1
                ? ""
                : filename.substring(delimiter + 1).toLowerCase(Locale.ROOT);
    }

    private static SpeechToTextException invalidFile(String message) {
        return new SpeechToTextException(SpeechToTextFailureType.INVALID_FILE, message);
    }
}
