package com.klist.chatbot.chat.presentation;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

final class TraceIdResolver {

    static final String HEADER_NAME = "X-Trace-Id";
    static final String REQUEST_ATTRIBUTE = TraceIdResolver.class.getName() + ".traceId";
    private static final int MAX_LENGTH = 100;

    private TraceIdResolver() {
    }

    static String resolve(String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.length() > MAX_LENGTH) {
            return UUID.randomUUID().toString();
        }
        return candidate;
    }

    static String resolve(HttpServletRequest request) {
        Object attribute = request.getAttribute(REQUEST_ATTRIBUTE);
        if (attribute instanceof String traceId && !traceId.isBlank()) {
            return traceId;
        }
        String traceId = resolve(request.getHeader(HEADER_NAME));
        request.setAttribute(REQUEST_ATTRIBUTE, traceId);
        return traceId;
    }
}
