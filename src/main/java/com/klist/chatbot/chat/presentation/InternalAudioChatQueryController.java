package com.klist.chatbot.chat.presentation;

import com.klist.chatbot.chat.application.InternalChatQueryUseCase;
import com.klist.chatbot.chat.presentation.dto.InternalAudioChatQueryRequest;
import com.klist.chatbot.chat.presentation.dto.InternalChatQueryResponse;
import com.klist.chatbot.speech.application.SpeechAudio;
import com.klist.chatbot.speech.application.SpeechToTextException;
import com.klist.chatbot.speech.application.SpeechToTextFailureType;
import com.klist.chatbot.speech.application.SpeechTranscriptionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/internal/chat")
public class InternalAudioChatQueryController {

    private final SpeechTranscriptionService transcriptionService;
    private final InternalChatQueryUseCase chatQueryUseCase;

    public InternalAudioChatQueryController(
            SpeechTranscriptionService transcriptionService,
            InternalChatQueryUseCase chatQueryUseCase
    ) {
        this.transcriptionService = transcriptionService;
        this.chatQueryUseCase = chatQueryUseCase;
    }

    @PostMapping(value = "/query/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InternalChatQueryResponse> query(
            @Valid @RequestPart("request") InternalAudioChatQueryRequest request,
            @RequestPart("audio") MultipartFile audio,
            HttpServletRequest servletRequest
    ) {
        String transcription = transcriptionService.transcribe(toSpeechAudio(audio));
        String traceId = TraceIdResolver.resolve(servletRequest);
        InternalChatQueryResponse response = chatQueryUseCase.query(
                request.toChatRequest(transcription),
                traceId
        );
        return ResponseEntity.ok()
                .header(TraceIdResolver.HEADER_NAME, traceId)
                .body(response);
    }

    private SpeechAudio toSpeechAudio(MultipartFile file) {
        try {
            return new SpeechAudio(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getBytes()
            );
        } catch (IOException exception) {
            throw new SpeechToTextException(
                    SpeechToTextFailureType.INVALID_FILE,
                    "Audio file could not be read",
                    exception
            );
        }
    }
}
