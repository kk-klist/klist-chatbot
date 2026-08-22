package com.klist.chatbot.chat.application.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.klist.chatbot.chat.application.prompt.ChatPrompt;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class LlmClientContractTest {

    @Test
    void delegatesGenerationRequestThroughProviderIndependentInterface() {
        LlmClient client = request -> new LlmGenerationResult(
                "서울 전망대를 추천합니다.",
                "test-model",
                120,
                30
        );
        LlmGenerationRequest request = new LlmGenerationRequest(
                new ChatPrompt("근거만 사용하세요.", "서울 야경 명소를 추천해줘"),
                Duration.ofSeconds(5)
        );

        LlmGenerationResult result = client.generate(request);

        assertThat(result.outputText()).isEqualTo("서울 전망대를 추천합니다.");
        assertThat(result.model()).isEqualTo("test-model");
        assertThat(result.totalTokens()).isEqualTo(150);
    }

    @Test
    void validatesRequestTimeoutAndGenerationResult() {
        ChatPrompt prompt = new ChatPrompt("시스템", "사용자");

        assertThatThrownBy(() -> new LlmGenerationRequest(prompt, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout");
        assertThatThrownBy(() -> new LlmGenerationRequest(prompt, Duration.ofMinutes(3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout");
        assertThatThrownBy(() -> new LlmGenerationResult(" ", "model", 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outputText");
        assertThatThrownBy(() -> new LlmGenerationResult("answer", "model", -1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token counts");
    }
}
