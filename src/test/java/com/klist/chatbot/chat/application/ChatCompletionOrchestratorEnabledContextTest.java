package com.klist.chatbot.chat.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.klist.chatbot.chat.application.llm.LlmClient;
import com.klist.chatbot.infrastructure.chat.idempotency.IdempotentInternalChatQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "llm.openai.enabled=true",
        "llm.openai.api-key=test-api-key"
})
class ChatCompletionOrchestratorEnabledContextTest {

    @Autowired
    private ChatCompletionOrchestrator completionOrchestrator;

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private InternalChatQueryUseCase chatQueryUseCase;

    @Test
    void registersCompletionFlowOnlyWhenLlmClientIsEnabled() {
        assertThat(completionOrchestrator).isNotNull();
        assertThat(llmClient).isNotNull();
        assertThat(chatQueryUseCase).isInstanceOf(IdempotentInternalChatQueryService.class);
    }
}
