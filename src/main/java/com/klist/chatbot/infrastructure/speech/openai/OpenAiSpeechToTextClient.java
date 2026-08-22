package com.klist.chatbot.infrastructure.speech.openai;

import com.klist.chatbot.speech.application.SpeechAudio;
import com.klist.chatbot.speech.application.SpeechToTextClient;
import com.klist.chatbot.speech.application.SpeechToTextException;
import com.klist.chatbot.speech.application.SpeechToTextFailureType;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

public class OpenAiSpeechToTextClient implements SpeechToTextClient {

    private final OpenAiSpeechToTextProperties properties;
    private final Function<Duration, RestClient> restClientFactory;

    public OpenAiSpeechToTextClient(OpenAiSpeechToTextProperties properties) {
        this(properties, timeout -> createRestClient(properties, timeout));
    }

    OpenAiSpeechToTextClient(
            OpenAiSpeechToTextProperties properties,
            Function<Duration, RestClient> restClientFactory
    ) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.restClientFactory = Objects.requireNonNull(
                restClientFactory,
                "restClientFactory must not be null"
        );
    }

    @Override
    public String transcribe(SpeechAudio audio, Duration timeout) {
        Objects.requireNonNull(audio, "audio must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (!properties.isEnabled()) {
            throw failure(SpeechToTextFailureType.CONFIGURATION, "STT provider is disabled", null);
        }

        try {
            TranscriptionResponse response = restClientFactory.apply(timeout).post()
                    .uri("/audio/transcriptions")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts(audio))
                    .retrieve()
                    .body(TranscriptionResponse.class);
            return response == null ? null : response.text();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 408
                    || exception.getStatusCode().value() == 504) {
                throw failure(SpeechToTextFailureType.TIMEOUT, "STT provider timed out", exception);
            }
            throw failure(
                    SpeechToTextFailureType.PROVIDER_UNAVAILABLE,
                    "STT provider request failed with status " + exception.getStatusCode().value(),
                    exception
            );
        } catch (ResourceAccessException exception) {
            SpeechToTextFailureType type = hasCause(exception, SocketTimeoutException.class)
                    ? SpeechToTextFailureType.TIMEOUT
                    : SpeechToTextFailureType.PROVIDER_UNAVAILABLE;
            throw failure(type, type == SpeechToTextFailureType.TIMEOUT
                    ? "STT provider timed out"
                    : "STT provider connection failed", exception);
        }
    }

    private static RestClient createRestClient(
            OpenAiSpeechToTextProperties properties,
            Duration timeout
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(shorter(properties.getConnectTimeout(), timeout));
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }

    private MultiValueMap<String, Object> parts(SpeechAudio audio) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("model", properties.getModel());
        if (properties.getLanguage() != null && !properties.getLanguage().isBlank()) {
            parts.add("language", properties.getLanguage());
        }
        ByteArrayResource resource = new ByteArrayResource(audio.content()) {
            @Override
            public String getFilename() {
                return audio.filename();
            }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(audio.contentType()));
        parts.add("file", new HttpEntity<>(resource, fileHeaders));
        return parts;
    }

    private static Duration shorter(Duration first, Duration second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static SpeechToTextException failure(
            SpeechToTextFailureType type,
            String message,
            Throwable cause
    ) {
        return new SpeechToTextException(type, message, cause);
    }

    private record TranscriptionResponse(String text) {
    }
}
