package com.klist.chatbot.chat.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.klist.chatbot.chat.application.InternalChatQueryUseCase;
import com.klist.chatbot.chat.presentation.dto.ChatQueryStatus;
import com.klist.chatbot.chat.presentation.dto.InternalChatQueryRequest;
import com.klist.chatbot.chat.presentation.dto.InternalChatQueryResponse;
import com.klist.chatbot.speech.application.SpeechAudio;
import com.klist.chatbot.speech.application.SpeechToTextException;
import com.klist.chatbot.speech.application.SpeechToTextFailureType;
import com.klist.chatbot.speech.application.SpeechTranscriptionService;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalAudioChatQueryController.class)
class InternalAudioChatQueryControllerTest {

    private static final String TRACE_ID = "audio-trace-001";
    private static final String INTERNAL_API_KEY = "test-internal-api-key";
    private static final UUID REQUEST_ID = UUID.fromString(
            "a22c717d-5a3e-46b5-92fc-f41624b85887"
    );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SpeechTranscriptionService transcriptionService;

    @MockitoBean
    private InternalChatQueryUseCase chatQueryUseCase;

    @Test
    void transcribesAudioAndPassesOnlyTextToExistingChatFlow() throws Exception {
        when(transcriptionService.transcribe(any())).thenReturn("서울 관광지를 추천해줘");
        when(chatQueryUseCase.query(any(), eq(TRACE_ID))).thenReturn(new InternalChatQueryResponse(
                REQUEST_ID,
                "경복궁을 추천합니다.",
                List.of(),
                List.of("부산 관광지도 알려줘"),
                TRACE_ID,
                ChatQueryStatus.COMPLETED,
                120L
        ));

        mockMvc.perform(multipart("/internal/chat/query/audio")
                        .file(requestPart())
                        .file(audioPart())
                        .header("X-Trace-Id", TRACE_ID)
                        .header(InternalApiKeyAuthenticationFilter.HEADER_NAME, INTERNAL_API_KEY))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", TRACE_ID))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.answer").value("경복궁을 추천합니다."))
                .andExpect(jsonPath("$.suggestions[0]").value("부산 관광지도 알려줘"));

        ArgumentCaptor<SpeechAudio> audioCaptor = ArgumentCaptor.forClass(SpeechAudio.class);
        verify(transcriptionService).transcribe(audioCaptor.capture());
        assertThat(audioCaptor.getValue().filename()).isEqualTo("question.wav");
        assertThat(audioCaptor.getValue().contentType()).isEqualTo("audio/wav");

        ArgumentCaptor<InternalChatQueryRequest> requestCaptor =
                ArgumentCaptor.forClass(InternalChatQueryRequest.class);
        verify(chatQueryUseCase).query(requestCaptor.capture(), eq(TRACE_ID));
        assertThat(requestCaptor.getValue().message()).isEqualTo("서울 관광지를 추천해줘");
        assertThat(requestCaptor.getValue().requestId()).isEqualTo(REQUEST_ID);
        assertThat(requestCaptor.getValue().context()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("speechFailures")
    void mapsSpeechFailuresToSafeApiContract(
            SpeechToTextFailureType failureType,
            int statusCode,
            String errorCode
    ) throws Exception {
        when(transcriptionService.transcribe(any())).thenThrow(new SpeechToTextException(
                failureType,
                "sensitive provider detail: secret-key"
        ));

        mockMvc.perform(multipart("/internal/chat/query/audio")
                        .file(requestPart())
                        .file(audioPart())
                        .header("X-Trace-Id", TRACE_ID)
                        .header(InternalApiKeyAuthenticationFilter.HEADER_NAME, INTERNAL_API_KEY))
                .andExpect(status().is(statusCode))
                .andExpect(header().string("X-Trace-Id", TRACE_ID))
                .andExpect(jsonPath("$.code").value(errorCode))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("secret-key")
                        )));
    }

    private static Stream<Arguments> speechFailures() {
        return Stream.of(
                Arguments.of(SpeechToTextFailureType.INVALID_FILE, 400, "STT_INVALID_FILE"),
                Arguments.of(SpeechToTextFailureType.FILE_TOO_LARGE, 413, "STT_FILE_TOO_LARGE"),
                Arguments.of(SpeechToTextFailureType.TIMEOUT, 504, "STT_TIMEOUT"),
                Arguments.of(SpeechToTextFailureType.EMPTY_RESULT, 422, "STT_EMPTY_RESULT"),
                Arguments.of(SpeechToTextFailureType.PROVIDER_UNAVAILABLE, 503, "STT_UNAVAILABLE"),
                Arguments.of(SpeechToTextFailureType.CONFIGURATION, 503, "STT_UNAVAILABLE")
        );
    }

    private static MockMultipartFile requestPart() {
        return new MockMultipartFile(
                "request",
                "request.json",
                MediaType.APPLICATION_JSON_VALUE,
                """
                {
                  "requestId": "a22c717d-5a3e-46b5-92fc-f41624b85887",
                  "sessionId": "session-001",
                  "userId": "user-001",
                  "timeoutMs": 5000
                }
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    private static MockMultipartFile audioPart() {
        return new MockMultipartFile(
                "audio",
                "question.wav",
                "audio/wav",
                new byte[]{1, 2, 3}
        );
    }
}
