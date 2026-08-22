package com.klist.chatbot.chat.presentation;

import com.klist.chatbot.chat.application.ChatQueryTimeoutException;
import com.klist.chatbot.chat.application.ChatProcessingFailedException;
import com.klist.chatbot.chat.application.ChatProcessingUnavailableException;
import com.klist.chatbot.chat.application.ChatRequestIdConflictException;
import com.klist.chatbot.chat.application.ChatRequestInProgressException;
import com.klist.chatbot.chat.presentation.error.InternalApiErrorResponse;
import com.klist.chatbot.chat.presentation.error.InternalApiErrorResponse.FieldViolation;
import com.klist.chatbot.chat.presentation.error.InternalChatApiErrorCode;
import com.klist.chatbot.speech.application.SpeechToTextException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
        InternalChatQueryController.class,
        InternalAudioChatQueryController.class
})
public class InternalChatApiExceptionHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(
            InternalChatApiExceptionHandler.class
    );

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<InternalApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String traceId = traceId(request);
        List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toViolation)
                .toList();
        InternalApiErrorResponse response = new InternalApiErrorResponse(
                InternalChatApiErrorCode.INVALID_REQUEST.name(),
                "The request is invalid.",
                traceId,
                java.time.Instant.now(),
                violations
        );
        return response(HttpStatus.BAD_REQUEST, traceId, response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<InternalApiErrorResponse> handleUnreadable(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        String traceId = traceId(request);
        return response(
                HttpStatus.BAD_REQUEST,
                traceId,
                InternalApiErrorResponse.of(
                        InternalChatApiErrorCode.INVALID_REQUEST.name(),
                        "The request body is invalid.",
                        traceId
                )
        );
    }

    @ExceptionHandler(ChatQueryTimeoutException.class)
    ResponseEntity<InternalApiErrorResponse> handleTimeout(
            ChatQueryTimeoutException exception,
            HttpServletRequest request
    ) {
        String traceId = traceId(request);
        return response(
                HttpStatus.GATEWAY_TIMEOUT,
                traceId,
                InternalApiErrorResponse.of(
                        InternalChatApiErrorCode.CHAT_QUERY_TIMEOUT.name(),
                        "The chatbot query timed out.",
                        traceId
                )
        );
    }

    @ExceptionHandler(ChatRequestInProgressException.class)
    ResponseEntity<InternalApiErrorResponse> handleRequestInProgress(
            ChatRequestInProgressException exception,
            HttpServletRequest request
    ) {
        String traceId = traceId(request);
        HttpHeaders headers = new HttpHeaders();
        headers.set(TraceIdResolver.HEADER_NAME, traceId);
        headers.set(HttpHeaders.RETRY_AFTER, "1");
        return new ResponseEntity<>(InternalApiErrorResponse.of(
                InternalChatApiErrorCode.REQUEST_IN_PROGRESS.name(),
                "The same request is still being processed.",
                traceId
        ), headers, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ChatRequestIdConflictException.class)
    ResponseEntity<InternalApiErrorResponse> handleRequestIdConflict(
            ChatRequestIdConflictException exception,
            HttpServletRequest request
    ) {
        String traceId = traceId(request);
        return response(
                HttpStatus.CONFLICT,
                traceId,
                InternalApiErrorResponse.of(
                        InternalChatApiErrorCode.REQUEST_ID_CONFLICT.name(),
                        "The request ID was already used for different content.",
                        traceId
                )
        );
    }

    @ExceptionHandler(ChatProcessingUnavailableException.class)
    ResponseEntity<InternalApiErrorResponse> handleUnavailable(
            ChatProcessingUnavailableException exception,
            HttpServletRequest request
    ) {
        String traceId = traceId(request);
        return response(
                HttpStatus.SERVICE_UNAVAILABLE,
                traceId,
                InternalApiErrorResponse.of(
                        InternalChatApiErrorCode.CHAT_PROCESSING_UNAVAILABLE.name(),
                        "Chat processing is not available.",
                        traceId
                )
        );
    }

    @ExceptionHandler(ChatProcessingFailedException.class)
    ResponseEntity<InternalApiErrorResponse> handleProcessingFailed(
            ChatProcessingFailedException exception,
            HttpServletRequest request
    ) {
        String traceId = traceId(request);
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                traceId,
                InternalApiErrorResponse.of(
                        InternalChatApiErrorCode.CHAT_PROCESSING_FAILED.name(),
                        "The chatbot response could not be validated.",
                        traceId
                )
        );
    }

    @ExceptionHandler(SpeechToTextException.class)
    ResponseEntity<InternalApiErrorResponse> handleSpeechToText(
            SpeechToTextException exception,
            HttpServletRequest request
    ) {
        String traceId = traceId(request);
        return switch (exception.failureType()) {
            case INVALID_FILE -> response(HttpStatus.BAD_REQUEST, traceId,
                    speechError(InternalChatApiErrorCode.STT_INVALID_FILE,
                            "The audio file is invalid or unsupported.", traceId));
            case FILE_TOO_LARGE -> response(HttpStatus.PAYLOAD_TOO_LARGE, traceId,
                    speechError(InternalChatApiErrorCode.STT_FILE_TOO_LARGE,
                            "The audio file is too large.", traceId));
            case TIMEOUT -> response(HttpStatus.GATEWAY_TIMEOUT, traceId,
                    speechError(InternalChatApiErrorCode.STT_TIMEOUT,
                            "Speech transcription timed out.", traceId));
            case EMPTY_RESULT -> response(HttpStatus.UNPROCESSABLE_ENTITY, traceId,
                    speechError(InternalChatApiErrorCode.STT_EMPTY_RESULT,
                            "No speech could be transcribed.", traceId));
            case PROVIDER_UNAVAILABLE, CONFIGURATION -> response(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    traceId,
                    speechError(InternalChatApiErrorCode.STT_UNAVAILABLE,
                            "Speech transcription is not available.", traceId)
            );
        };
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<InternalApiErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        String traceId = traceId(request);
        log.error("Unexpected chatbot request failure. traceId={}", traceId, exception);
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                traceId,
                InternalApiErrorResponse.of(
                        InternalChatApiErrorCode.INTERNAL_ERROR.name(),
                        "The chatbot request could not be processed.",
                        traceId
                )
        );
    }

    private FieldViolation toViolation(FieldError error) {
        return new FieldViolation(error.getField(), error.getDefaultMessage());
    }

    private InternalApiErrorResponse speechError(
            InternalChatApiErrorCode code,
            String message,
            String traceId
    ) {
        return InternalApiErrorResponse.of(code.name(), message, traceId);
    }

    private String traceId(HttpServletRequest request) {
        return TraceIdResolver.resolve(request);
    }

    private ResponseEntity<InternalApiErrorResponse> response(
            HttpStatus status,
            String traceId,
            InternalApiErrorResponse body
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(TraceIdResolver.HEADER_NAME, traceId);
        return new ResponseEntity<>(body, headers, status);
    }
}
