package com.klist.chatbot.chat.presentation;

import com.klist.chatbot.chat.presentation.error.InternalApiErrorResponse;
import com.klist.chatbot.chat.presentation.error.InternalChatApiErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class InternalApiKeyAuthenticationFilter extends OncePerRequestFilter {

    static final String HEADER_NAME = "X-Internal-Api-Key";

    private static final Logger log = LoggerFactory.getLogger(InternalApiKeyAuthenticationFilter.class);

    private final String configuredKey;
    private final ObjectMapper objectMapper;

    public InternalApiKeyAuthenticationFilter(
            @Value("${internal-api.auth.key:}") String configuredKey,
            ObjectMapper objectMapper
    ) {
        this.configuredKey = configuredKey == null ? "" : configuredKey;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (configuredKey.isBlank()) {
            log.error("Internal API authentication key is not configured");
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    InternalChatApiErrorCode.INTERNAL_AUTH_NOT_CONFIGURED,
                    "Internal API authentication is not available.", request);
            return;
        }

        String providedKey = request.getHeader(HEADER_NAME);
        if (!matches(configuredKey, providedKey)) {
            log.warn("Internal API authentication failed");
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    InternalChatApiErrorCode.UNAUTHORIZED,
                    "Internal API authentication failed.", request);
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.equals("/internal") || path.startsWith("/internal/"));
    }

    private static boolean matches(String configuredKey, String providedKey) {
        if (providedKey == null) {
            return false;
        }
        return MessageDigest.isEqual(
                configuredKey.getBytes(StandardCharsets.UTF_8),
                providedKey.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void writeError(
            HttpServletResponse response,
            int status,
            InternalChatApiErrorCode code,
            String message,
            HttpServletRequest request
    ) throws IOException {
        String traceId = TraceIdResolver.resolve(request);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(TraceIdResolver.HEADER_NAME, traceId);
        objectMapper.writeValue(
                response.getOutputStream(),
                InternalApiErrorResponse.of(code.name(), message, traceId)
        );
    }
}
