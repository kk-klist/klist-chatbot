package com.klist.chatbot.infrastructure.search.index;

import com.klist.chatbot.infrastructure.search.document.TouristSpotSearchDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;

public class ElasticsearchTouristSpotIndexingGateway implements TouristSpotIndexingGateway {

    private final ElasticsearchOperations operations;
    private final IndexCoordinates indexCoordinates;

    public ElasticsearchTouristSpotIndexingGateway(
            ElasticsearchOperations operations,
            TouristSpotIndexProperties properties
    ) {
        this.operations = operations;
        properties.validate();
        this.indexCoordinates = IndexCoordinates.of(properties.getAlias());
    }

    @Override
    public TouristSpotSearchDocument save(TouristSpotSearchDocument document) {
        validateDocument(document);
        try {
            return operations.save(document, indexCoordinates);
        } catch (RuntimeException exception) {
            throw failure(TouristSpotIndexOperation.SAVE, "Unable to save a tourist spot search document.", exception);
        }
    }

    @Override
    public List<TouristSpotSearchDocument> saveAll(List<TouristSpotSearchDocument> documents) {
        return saveAll(documents, indexCoordinates.getIndexName());
    }

    @Override
    public List<TouristSpotSearchDocument> saveAll(
            List<TouristSpotSearchDocument> documents,
            String indexName
    ) {
        if (documents == null) {
            throw new IllegalArgumentException("documents must not be null.");
        }
        if (indexName == null || indexName.isBlank()) {
            throw new IllegalArgumentException("indexName must not be blank.");
        }
        documents.forEach(this::validateDocument);
        if (documents.isEmpty()) {
            return List.of();
        }

        List<IndexQuery> queries = documents.stream()
                .map(document -> new IndexQueryBuilder()
                        .withId(document.touristSpotId().toString())
                        .withObject(document)
                        .build())
                .toList();
        try {
            operations.bulkIndex(queries, IndexCoordinates.of(indexName));
            return List.copyOf(documents);
        } catch (RuntimeException exception) {
            throw failure(
                    TouristSpotIndexOperation.BULK_SAVE,
                    "Unable to bulk save tourist spot search documents.",
                    exception
            );
        }
    }

    @Override
    public void delete(Long touristSpotId) {
        validateId(touristSpotId);
        try {
            operations.delete(touristSpotId.toString(), indexCoordinates);
        } catch (RuntimeException exception) {
            throw failure(
                    TouristSpotIndexOperation.DELETE,
                    "Unable to delete a tourist spot search document.",
                    exception
            );
        }
    }

    @Override
    public boolean exists(Long touristSpotId) {
        validateId(touristSpotId);
        try {
            return operations.exists(touristSpotId.toString(), indexCoordinates);
        } catch (RuntimeException exception) {
            throw failure(
                    TouristSpotIndexOperation.EXISTS,
                    "Unable to check a tourist spot search document.",
                    exception
            );
        }
    }

    @Override
    public Optional<TouristSpotSearchDocument> findById(Long touristSpotId) {
        validateId(touristSpotId);
        try {
            return Optional.ofNullable(operations.get(
                    touristSpotId.toString(),
                    TouristSpotSearchDocument.class,
                    indexCoordinates
            ));
        } catch (RuntimeException exception) {
            throw failure(
                    TouristSpotIndexOperation.GET,
                    "Unable to get a tourist spot search document.",
                    exception
            );
        }
    }

    private void validateDocument(TouristSpotSearchDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("document must not be null.");
        }
        validateId(document.touristSpotId());
    }

    private void validateId(Long touristSpotId) {
        if (touristSpotId == null || touristSpotId <= 0) {
            throw new IllegalArgumentException("touristSpotId must be positive.");
        }
    }

    private TouristSpotIndexingException failure(
            TouristSpotIndexOperation operation,
            String message,
            RuntimeException cause
    ) {
        return new TouristSpotIndexingException(operation, message, cause);
    }
}
