package com.klist.chatbot.chat.presentation;

import com.klist.chatbot.chat.application.InternalChatQueryUseCase;
import com.klist.chatbot.chat.presentation.dto.InternalChatQueryRequest;
import com.klist.chatbot.chat.presentation.dto.InternalChatQueryResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/chat")
public class InternalChatQueryController {

    private final InternalChatQueryUseCase chatQueryUseCase;

    public InternalChatQueryController(InternalChatQueryUseCase chatQueryUseCase) {
        this.chatQueryUseCase = chatQueryUseCase;
    }

    @PostMapping("/query")
    public ResponseEntity<InternalChatQueryResponse> query(
            @Valid @RequestBody InternalChatQueryRequest request,
            HttpServletRequest servletRequest
    ) {
        String traceId = TraceIdResolver.resolve(servletRequest);
        InternalChatQueryResponse response = chatQueryUseCase.query(request, traceId);
        return ResponseEntity.ok()
                .header(TraceIdResolver.HEADER_NAME, traceId)
                .body(response);
    }
}
