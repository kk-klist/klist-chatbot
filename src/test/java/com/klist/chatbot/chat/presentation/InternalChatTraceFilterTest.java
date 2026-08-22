package com.klist.chatbot.chat.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalChatTraceFilterTest {

    private final InternalChatTraceFilter filter = new InternalChatTraceFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void exposesTraceIdThroughRequestResponseAndMdcDuringChatRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/internal/chat/query"
        );
        request.addHeader(TraceIdResolver.HEADER_NAME, "trace-001");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (filteredRequest, filteredResponse) -> {
            assertThat(MDC.get(InternalChatTraceFilter.MDC_KEY)).isEqualTo("trace-001");
            assertThat(filteredRequest.getAttribute(TraceIdResolver.REQUEST_ATTRIBUTE))
                    .isEqualTo("trace-001");
        });

        assertThat(response.getHeader(TraceIdResolver.HEADER_NAME)).isEqualTo("trace-001");
        assertThat(MDC.get(InternalChatTraceFilter.MDC_KEY)).isNull();
    }

    @Test
    void generatesTraceIdAndRestoresPreviousMdcValue() throws Exception {
        MDC.put(InternalChatTraceFilter.MDC_KEY, "outer-trace");
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/internal/chat/query"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (filteredRequest, filteredResponse) ->
                assertThat(MDC.get(InternalChatTraceFilter.MDC_KEY))
                        .isNotBlank()
                        .isNotEqualTo("outer-trace"));

        assertThat(response.getHeader(TraceIdResolver.HEADER_NAME)).isNotBlank();
        assertThat(MDC.get(InternalChatTraceFilter.MDC_KEY)).isEqualTo("outer-trace");
    }

    @Test
    void ignoresNonChatRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (filteredRequest, filteredResponse) ->
                assertThat(MDC.get(InternalChatTraceFilter.MDC_KEY)).isNull());

        assertThat(response.getHeader(TraceIdResolver.HEADER_NAME)).isNull();
    }

    @Test
    void logsStructuredRequestCompletionFieldsWithTraceId() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(InternalChatTraceFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MockHttpServletRequest request = new MockHttpServletRequest(
                    "POST", "/internal/chat/query"
            );
            request.addHeader(TraceIdResolver.HEADER_NAME, "trace-structured-001");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, (filteredRequest, filteredResponse) ->
                    ((MockHttpServletResponse) filteredResponse).setStatus(202));

            ILoggingEvent event = appender.list.get(appender.list.size() - 1);
            Map<String, Object> fields = event.getKeyValuePairs().stream()
                    .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
            assertThat(event.getMDCPropertyMap()).containsEntry("traceId", "trace-structured-001");
            assertThat(fields)
                    .containsEntry("event", "internal_chat_request_completed")
                    .containsEntry("httpMethod", "POST")
                    .containsEntry("path", "/internal/chat/query")
                    .containsEntry("status", 202);
            assertThat(fields.get("durationMs")).isInstanceOf(Long.class);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
