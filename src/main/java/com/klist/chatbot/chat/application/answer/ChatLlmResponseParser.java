package com.klist.chatbot.chat.application.answer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.klist.chatbot.chat.application.llm.LlmGenerationResult;
import java.util.Objects;

public class ChatLlmResponseParser {

    private final ObjectMapper objectMapper;

    public ChatLlmResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null")
                .copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public ChatGeneratedAnswer parse(LlmGenerationResult generationResult) {
        Objects.requireNonNull(generationResult, "generationResult must not be null");
        try {
            ChatGeneratedAnswer answer = objectMapper.readValue(
                    generationResult.outputText(),
                    ChatGeneratedAnswer.class
            );
            if (answer == null) {
                throw new ChatLlmResponseParsingException("LLM structured response is null", null);
            }
            return answer;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new ChatLlmResponseParsingException(
                    "LLM structured response is invalid",
                    exception
            );
        }
    }
}
