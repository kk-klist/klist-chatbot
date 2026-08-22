package com.klist.chatbot.chat.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class InternalChatQueryRequestValidationTest {

    private static final UUID REQUEST_ID = UUID.fromString(
            "a22c717d-5a3e-46b5-92fc-f41624b85887"
    );

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsValidRequestAndAppliesDefaultTimeout() {
        InternalChatQueryRequest request = new InternalChatQueryRequest(
                REQUEST_ID,
                "session-001",
                "user-001",
                "서울 야경 명소를 추천해줘",
                List.of(),
                null
        );

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.effectiveTimeoutMs()).isEqualTo(30000);
    }

    @Test
    void generatesRequestIdAndUsesSessionIdForMissingUserId() {
        InternalChatQueryRequest request = new InternalChatQueryRequest(
                null, "3b086657-4887-49da-938e-23b1e0efd2b8", null,
                "서울에서 방문할 만한 관광지를 추천해줘", null, null
        );

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.requestId()).isNotNull();
        assertThat(request.userId()).isEqualTo(request.sessionId());
        assertThat(request.context()).isEmpty();
    }

    @Test
    void acceptsInclusiveTimeoutBoundaries() {
        InternalChatQueryRequest minimum = request("message", 100);
        InternalChatQueryRequest maximum = request("message", 30000);

        assertThat(validator.validate(minimum)).isEmpty();
        assertThat(validator.validate(maximum)).isEmpty();
        assertThat(minimum.effectiveTimeoutMs()).isEqualTo(100);
        assertThat(maximum.effectiveTimeoutMs()).isEqualTo(30000);
    }

    @Test
    void rejectsBlankIdentifiersAndMessage() {
        InternalChatQueryRequest request = new InternalChatQueryRequest(
                REQUEST_ID, " ", "", "\t", List.of(), 5000
        );

        assertThat(violatedFields(request))
                .containsExactlyInAnyOrder("sessionId", "userId", "message");
    }

    @Test
    void rejectsOversizedFieldsAndOutOfRangeTimeout() {
        InternalChatQueryRequest oversized = new InternalChatQueryRequest(
                REQUEST_ID,
                "s".repeat(101),
                "u".repeat(101),
                "m".repeat(4001),
                List.of(),
                30001
        );

        assertThat(violatedFields(oversized))
                .containsExactlyInAnyOrder("sessionId", "userId", "message", "timeoutMs");
        assertThat(violatedFields(request("message", 99))).containsExactly("timeoutMs");
    }

    @Test
    void acceptsUpToTenContextMessagesAndRejectsMore() {
        List<ChatContextMessage> tenMessages = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> new ChatContextMessage(
                        index % 2 == 0 ? ChatContextRole.USER : ChatContextRole.ASSISTANT,
                        "message-" + index
                ))
                .toList();
        InternalChatQueryRequest accepted = new InternalChatQueryRequest(
                REQUEST_ID, "session-001", "user-001", "질문", tenMessages, 5000
        );
        InternalChatQueryRequest rejected = new InternalChatQueryRequest(
                REQUEST_ID,
                "session-001",
                "user-001",
                "질문",
                java.util.stream.Stream.concat(
                        tenMessages.stream(),
                        java.util.stream.Stream.of(new ChatContextMessage(
                                ChatContextRole.USER, "eleventh"
                        ))
                ).toList(),
                5000
        );

        assertThat(validator.validate(accepted)).isEmpty();
        assertThat(violatedFields(rejected)).containsExactly("context");
    }

    @Test
    void rejectsInvalidContextMessage() {
        InternalChatQueryRequest request = new InternalChatQueryRequest(
                REQUEST_ID,
                "session-001",
                "user-001",
                "질문",
                List.of(new ChatContextMessage(null, " ")),
                5000
        );

        assertThat(violatedFields(request))
                .containsExactlyInAnyOrder("context[0].role", "context[0].content");
    }

    private Set<String> violatedFields(InternalChatQueryRequest request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private static InternalChatQueryRequest request(String message, Integer timeoutMs) {
        return new InternalChatQueryRequest(
                REQUEST_ID, "session-001", "user-001", message, List.of(), timeoutMs
        );
    }
}
