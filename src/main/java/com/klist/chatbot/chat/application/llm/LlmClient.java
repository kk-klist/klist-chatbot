package com.klist.chatbot.chat.application.llm;

public interface LlmClient {

    LlmGenerationResult generate(LlmGenerationRequest request);
}
