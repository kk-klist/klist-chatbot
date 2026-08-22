package com.klist.chatbot.chat.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class InternalApiKeyAuthenticationFilterTest {

    private static final String API_KEY = "test-internal-api-key";

    private final InternalApiKeyAuthenticationFilter filter = new InternalApiKeyAuthenticationFilter(
            API_KEY,
            JsonMapper.builder().build()
    );

    @Test
    void allowsInternalRequestWithMatchingKey() throws Exception {
        MockHttpServletRequest request = internalRequest();
        request.addHeader(InternalApiKeyAuthenticationFilter.HEADER_NAME, API_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (filteredRequest, filteredResponse) ->
                invoked.set(true));

        assertThat(invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsInternalRequestWithoutKey() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(internalRequest(), response, (request, filteredResponse) -> {
            throw new AssertionError("filter chain must not be invoked");
        });

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHORIZED\"");
        assertThat(response.getHeader(TraceIdResolver.HEADER_NAME)).isNotBlank();
    }

    @Test
    void rejectsInternalRequestWithWrongKey() throws Exception {
        MockHttpServletRequest request = internalRequest();
        request.addHeader(InternalApiKeyAuthenticationFilter.HEADER_NAME, "wrong-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (filteredRequest, filteredResponse) -> {
            throw new AssertionError("filter chain must not be invoked");
        });

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).doesNotContain("wrong-key", API_KEY);
    }

    @Test
    void failsClosedWhenConfiguredKeyIsBlank() throws Exception {
        InternalApiKeyAuthenticationFilter unconfiguredFilter = new InternalApiKeyAuthenticationFilter(
                "",
                JsonMapper.builder().build()
        );
        MockHttpServletRequest request = internalRequest();
        request.addHeader(InternalApiKeyAuthenticationFilter.HEADER_NAME, API_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        unconfiguredFilter.doFilter(request, response, (filteredRequest, filteredResponse) -> {
            throw new AssertionError("filter chain must not be invoked");
        });

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString())
                .contains("\"code\":\"INTERNAL_AUTH_NOT_CONFIGURED\"");
    }

    @Test
    void ignoresNonInternalRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (filteredRequest, filteredResponse) ->
                invoked.set(true));

        assertThat(invoked).isTrue();
    }

    private static MockHttpServletRequest internalRequest() {
        return new MockHttpServletRequest("POST", "/internal/chat/query");
    }
}
