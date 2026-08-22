package com.klist.chatbot.infrastructure.search.index;

import com.klist.chatbot.infrastructure.search.document.TouristSpotSearchDocument;
import java.util.List;
import java.util.Optional;

public interface TouristSpotIndexingGateway {

    TouristSpotSearchDocument save(TouristSpotSearchDocument document);

    List<TouristSpotSearchDocument> saveAll(List<TouristSpotSearchDocument> documents);

    List<TouristSpotSearchDocument> saveAll(
            List<TouristSpotSearchDocument> documents,
            String indexName
    );

    void delete(Long touristSpotId);

    boolean exists(Long touristSpotId);

    Optional<TouristSpotSearchDocument> findById(Long touristSpotId);
}
