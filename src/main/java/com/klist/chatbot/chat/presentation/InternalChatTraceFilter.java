package com.klist.chatbot.chat.presentation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalChatTraceFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalChatTraceFilter.class);
    static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = TraceIdResolver.resolve(request.getHeader(TraceIdResolver.HEADER_NAME));
        String previousTraceId = MDC.get(MDC_KEY);
        request.setAttribute(TraceIdResolver.REQUEST_ATTRIBUTE, traceId);
        response.setHeader(TraceIdResolver.HEADER_NAME, traceId);
        MDC.put(MDC_KEY, traceId);
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            log.atInfo()
                    .addKeyValue("event", "internal_chat_request_completed")
                    .addKeyValue("httpMethod", request.getMethod())
                    .addKeyValue("path", request.getRequestURI())
                    .addKeyValue("status", response.getStatus())
                    .addKeyValue("durationMs", elapsedMillis(startedAt))
                    .log("Internal chat request completed");
            restorePreviousTraceId(previousTraceId);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.equals("/internal/chat") || path.startsWith("/internal/chat/"));
    }

    private static void restorePreviousTraceId(String previousTraceId) {
        if (previousTraceId == null) {
            MDC.remove(MDC_KEY);
        } else {
            MDC.put(MDC_KEY, previousTraceId);
        }
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0, System.nanoTime() - startedAt));
    }
}
