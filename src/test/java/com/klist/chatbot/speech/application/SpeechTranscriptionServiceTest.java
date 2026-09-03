package com.klist.chatbot.speech.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SpeechTranscriptionServiceTest {

    private final SpeechToTextClient client = mock(SpeechToTextClient.class);
    private SpeechTranscriptionService service;

    @BeforeEach
    void setUp() {
        SpeechTranscriptionProperties properties = new SpeechTranscriptionProperties();
        properties.setMaxFileSize(10);
        properties.setTimeout(Duration.ofSeconds(3));
        service = new SpeechTranscriptionService(client, properties);
    }

    @ParameterizedTest
    @MethodSource("supportedAudio")
    void acceptsSupportedFormatAndReturnsTrimmedText(String filename, String contentType) {
        SpeechAudio audio = audio(filename, contentType, new byte[]{1});
        when(client.transcribe(audio, Duration.ofSeconds(3))).thenReturn("  서울 관광지 추천  ");

        assertThat(service.transcribe(audio)).isEqualTo("서울 관광지 추천");
        verify(client).transcribe(audio, Duration.ofSeconds(3));
    }

    @Test
    void normalizesParameterizedWebmContentType() {
        SpeechAudio audio = audio("voice.webm", " Audio/WebM ; codecs=opus ", new byte[]{1});

        assertThat(audio.contentType()).isEqualTo("audio/webm");
    }

    @Test
    void rejectsEmptyUnsupportedAndOversizedFiles() {
        assertFailure(audio("voice.wav", "audio/wav", new byte[0]),
                SpeechToTextFailureType.INVALID_FILE);
        assertFailure(audio("voice.txt", "text/plain", new byte[]{1}),
                SpeechToTextFailureType.INVALID_FILE);
        assertFailure(audio("voice.wav", "application/octet-stream", new byte[]{1}),
                SpeechToTextFailureType.INVALID_FILE);
        assertFailure(audio("voice.wav", "audio/wav", new byte[11]),
                SpeechToTextFailureType.FILE_TOO_LARGE);
    }

    @Test
    void rejectsEmptyTranscription() {
        SpeechAudio audio = audio("voice.wav", "audio/wav", new byte[]{1});
        when(client.transcribe(audio, Duration.ofSeconds(3))).thenReturn("  ");

        assertThatThrownBy(() -> service.transcribe(audio))
                .isInstanceOfSatisfying(SpeechToTextException.class, exception ->
                        assertThat(exception.failureType())
                                .isEqualTo(SpeechToTextFailureType.EMPTY_RESULT));
    }

    private void assertFailure(SpeechAudio audio, SpeechToTextFailureType expected) {
        assertThatThrownBy(() -> service.transcribe(audio))
                .isInstanceOfSatisfying(SpeechToTextException.class, exception ->
                        assertThat(exception.failureType()).isEqualTo(expected));
    }

    private static Stream<Arguments> supportedAudio() {
        return Stream.of(
                Arguments.of("voice.mp3", "audio/mpeg"),
                Arguments.of("voice.mp4", "audio/mp4"),
                Arguments.of("voice.mpeg", "video/mpeg"),
                Arguments.of("voice.mpga", "audio/mpeg"),
                Arguments.of("voice.m4a", "audio/x-m4a"),
                Arguments.of("voice.wav", "audio/wav"),
                Arguments.of("voice.webm", "audio/webm"),
                Arguments.of("voice.webm", "audio/webm;codecs=opus"),
                Arguments.of("voice.webm", " Audio/WebM ; codecs=opus ")
        );
    }

    private static SpeechAudio audio(String filename, String contentType, byte[] content) {
        return new SpeechAudio(filename, contentType, content);
    }
}
