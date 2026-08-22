package com.klist.chatbot.chat.application;

import com.klist.chatbot.chat.application.analysis.ChatQuestionAnalysis;
import com.klist.chatbot.chat.application.analysis.ChatQuestionAnalyzer;
import com.klist.chatbot.chat.application.evidence.ChatEvidenceContext;
import com.klist.chatbot.chat.application.evidence.ChatSearchEvidenceOrganizer;
import com.klist.chatbot.chat.application.prompt.ChatPromptFactory;
import com.klist.chatbot.chat.application.prompt.ChatPromptPreparation;
import com.klist.chatbot.chat.application.prompt.ChatConversationMessage;
import com.klist.chatbot.search.application.TouristSpotRetriever;
import com.klist.chatbot.search.application.TouristSpotSearchResult;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public class ChatSearchOrchestrator {

    private final ChatQuestionAnalyzer questionAnalyzer;
    private final TouristSpotRetriever touristSpotRetriever;
    private final ChatSearchEvidenceOrganizer evidenceOrganizer;
    private final ChatPromptFactory promptFactory;

    public ChatSearchOrchestrator(
            ChatQuestionAnalyzer questionAnalyzer,
            TouristSpotRetriever touristSpotRetriever,
            ChatSearchEvidenceOrganizer evidenceOrganizer,
            ChatPromptFactory promptFactory
    ) {
        this.questionAnalyzer = Objects.requireNonNull(
                questionAnalyzer,
                "questionAnalyzer must not be null"
        );
        this.touristSpotRetriever = Objects.requireNonNull(
                touristSpotRetriever,
                "touristSpotRetriever must not be null"
        );
        this.evidenceOrganizer = Objects.requireNonNull(
                evidenceOrganizer,
                "evidenceOrganizer must not be null"
        );
        this.promptFactory = Objects.requireNonNull(promptFactory, "promptFactory must not be null");
    }

    public ChatSearchResult search(String question) {
        return search(question, List.of());
    }

    public ChatSearchResult search(String question, List<ChatConversationMessage> context) {
        return search(question, context, null);
    }

    public ChatSearchResult search(
            String question,
            List<ChatConversationMessage> context,
            Duration timeout
    ) {
        ChatQuestionAnalysis analysis = questionAnalyzer.analyze(question);
        TouristSpotSearchResult searchResult = timeout == null
                ? touristSpotRetriever.retrieve(analysis.searchCriteria())
                : touristSpotRetriever.retrieve(analysis.searchCriteria(), timeout);
        ChatEvidenceContext evidenceContext = evidenceOrganizer.organize(searchResult);
        ChatPromptPreparation promptPreparation = context.isEmpty()
                ? promptFactory.prepare(analysis.originalQuestion(), evidenceContext)
                : promptFactory.prepare(analysis.originalQuestion(), context, evidenceContext);
        return new ChatSearchResult(analysis, searchResult, evidenceContext, promptPreparation);
    }
}
