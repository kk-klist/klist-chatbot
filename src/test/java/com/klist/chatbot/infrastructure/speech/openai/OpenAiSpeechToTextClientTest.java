package com.klist.chatbot.infrastructure.speech.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.klist.chatbot.speech.application.SpeechAudio;
import com.klist.chatbot.speech.application.SpeechToTextException;
import com.klist.chatbot.speech.application.SpeechToTextFailureType;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiSpeechToTextClientTest {

    private static final String BASE_URL = "https://api.openai.test/v1";

    private OpenAiSpeechToTextProperties properties;
    private MockRestServiceServer server;
    private OpenAiSpeechToTextClient client;
    private AtomicReference<Duration> requestedTimeout;

    @BeforeEach
    void setUp() {
        properties = properties();
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        requestedTimeout = new AtomicReference<>();
        client = new OpenAiSpeechToTextClient(properties, timeout -> {
            requestedTimeout.set(timeout);
            return restClient;
        });
    }

    @Test
    void sendsMultipartTranscriptionRequestAndReturnsText() {
        server.expect(requestTo(BASE_URL + "/audio/transcriptions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-stt-api-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "gpt-4o-mini-transcribe"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("question.wav")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"language\"")))
                .andRespond(withSuccess("{\"text\":\"서울 관광지를 추천해줘\"}",
                        MediaType.APPLICATION_JSON));

        String result = client.transcribe(audio(), Duration.ofSeconds(5));

        assertThat(result).isEqualTo("서울 관광지를 추천해줘");
        assertThat(requestedTimeout.get()).isEqualTo(Duration.ofSeconds(5));
        server.verify();
    }

    @Test
    void classifiesProviderTimeoutAndFailureWithoutExposingApiKey() {
        server.expect(requestTo(BASE_URL + "/audio/transcriptions"))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));

        assertFailure(SpeechToTextFailureType.TIMEOUT);
        server.verify();

        setUp();
        server.expect(requestTo(BASE_URL + "/audio/transcriptions"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertFailure(SpeechToTextFailureType.PROVIDER_UNAVAILABLE);
        server.verify();
    }

    @Test
    void classifiesNetworkTimeout() {
        RestClient timeoutClient = RestClient.builder()
                .requestFactory((uri, method) -> {
                    throw new SocketTimeoutException("simulated timeout");
                })
                .build();
        client = new OpenAiSpeechToTextClient(properties, timeout -> timeoutClient);

        assertFailure(SpeechToTextFailureType.TIMEOUT);
    }

    @Test
    void failsClosedWhenProviderIsDisabled() {
        properties.setEnabled(false);

        assertFailure(SpeechToTextFailureType.CONFIGURATION);
    }

    private void assertFailure(SpeechToTextFailureType expected) {
        assertThatThrownBy(() -> client.transcribe(audio(), Duration.ofSeconds(5)))
                .isInstanceOfSatisfying(SpeechToTextException.class, exception -> {
                    assertThat(exception.failureType()).isEqualTo(expected);
                    assertThat(exception.getMessage()).doesNotContain("test-stt-api-key");
                });
    }

    private static SpeechAudio audio() {
        return new SpeechAudio("question.wav", "audio/wav", new byte[]{1, 2, 3});
    }

    private static OpenAiSpeechToTextProperties properties() {
        OpenAiSpeechToTextProperties properties = new OpenAiSpeechToTextProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(java.net.URI.create(BASE_URL));
        properties.setApiKey("test-stt-api-key");
        return properties;
    }
}
