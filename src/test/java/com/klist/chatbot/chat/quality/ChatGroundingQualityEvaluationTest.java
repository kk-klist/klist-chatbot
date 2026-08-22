package com.klist.chatbot.chat.quality;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.klist.chatbot.chat.application.answer.ChatAnswerGroundingException;
import com.klist.chatbot.chat.application.answer.ChatGeneratedAnswer;
import com.klist.chatbot.chat.application.answer.ChatGeneratedAnswerValidator;
import com.klist.chatbot.chat.application.answer.ChatGroundingViolation;
import com.klist.chatbot.chat.application.answer.ChatRecommendation;
import com.klist.chatbot.chat.application.evidence.ChatEvidenceContext;
import com.klist.chatbot.chat.application.evidence.ChatTouristSpotEvidence;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatGroundingQualityEvaluationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatGeneratedAnswerValidator validator = new ChatGeneratedAnswerValidator();

    @Test
    void acceptsGroundedAnswersAndRejectsStructuredFactHallucinations() throws IOException {
        List<EvaluationCase> cases = objectMapper.readValue(
                getClass().getResourceAsStream("/chat/chat-grounding-evaluation.json"),
                new TypeReference<>() { }
        );
        int correct = 0;
        int accepted = 0;
        int rejected = 0;

        for (EvaluationCase evaluation : cases) {
            EvaluationOutcome outcome = evaluate(evaluation);
            if (outcome.valid() == evaluation.expectedValid()
                    && outcome.violation() == evaluation.expectedViolation()) {
                correct++;
            }
            if (outcome.valid()) {
                accepted++;
            } else {
                rejected++;
            }
        }

        assertThat(cases).hasSize(7);
        assertThat(accepted).isEqualTo(2);
        assertThat(rejected).isEqualTo(5);
        assertThat((double) correct / cases.size()).isEqualTo(1.0);
    }

    private EvaluationOutcome evaluate(EvaluationCase evaluation) {
        ChatGeneratedAnswer answer = new ChatGeneratedAnswer(
                evaluation.answer(),
                evaluation.recommendations()
        );
        List<ChatTouristSpotEvidence> evidence = evaluation.evidence().stream()
                .map(EvidenceFixture::toEvidence)
                .toList();
        try {
            validator.validate(
                    answer,
                    new ChatEvidenceContext(evidence, evidence.size(), Duration.ZERO)
            );
            return new EvaluationOutcome(true, null);
        } catch (ChatAnswerGroundingException exception) {
            return new EvaluationOutcome(false, exception.violation());
        }
    }

    private record EvaluationCase(
            String question,
            String answer,
            List<ChatRecommendation> recommendations,
            List<EvidenceFixture> evidence,
            boolean expectedValid,
            ChatGroundingViolation expectedViolation
    ) {
    }

    private record EvidenceFixture(
            Long touristSpotId,
            String title,
            String phoneNumber,
            String openingHours,
            String admissionFee,
            String reservationUrl
    ) {

        ChatTouristSpotEvidence toEvidence() {
            return new ChatTouristSpotEvidence(
                    touristSpotId, title, null, null, null, null, null,
                    null, phoneNumber, openingHours, admissionFee, reservationUrl, 1.0f
            );
        }
    }

    private record EvaluationOutcome(boolean valid, ChatGroundingViolation violation) {
    }
}
