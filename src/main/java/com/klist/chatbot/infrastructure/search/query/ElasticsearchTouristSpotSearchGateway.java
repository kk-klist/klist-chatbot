package com.klist.chatbot.infrastructure.search.query;

import co.elastic.clients.elasticsearch._types.GeoLocation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.klist.chatbot.infrastructure.search.document.TouristSpotSearchDocument;
import com.klist.chatbot.infrastructure.search.index.TouristSpotIndexProperties;
import com.klist.chatbot.search.application.TouristSpotSearchCriteria;
import com.klist.chatbot.search.application.TouristSpotSearchEvidence;
import com.klist.chatbot.search.application.TouristSpotSearchGateway;
import com.klist.chatbot.search.application.TouristSpotSearchResult;
import java.util.ArrayList;
import java.util.List;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilterBuilder;

public class ElasticsearchTouristSpotSearchGateway implements TouristSpotSearchGateway {

    private static final List<String> WEIGHTED_FIELDS = List.of(
            "title^5",
            "address^3",
            "description^2",
            "openingHours",
            "admissionFee"
    );

    private final ElasticsearchOperations operations;
    private final IndexCoordinates indexCoordinates;

    public ElasticsearchTouristSpotSearchGateway(
            ElasticsearchOperations operations,
            TouristSpotIndexProperties properties
    ) {
        this.operations = operations;
        properties.validate();
        this.indexCoordinates = IndexCoordinates.of(properties.getAlias());
    }

    @Override
    public TouristSpotSearchResult search(TouristSpotSearchCriteria criteria) {
        if (criteria == null) {
            throw new IllegalArgumentException("criteria must not be null.");
        }
        NativeQuery query = buildQuery(criteria);
        try {
            SearchHits<TouristSpotSearchDocument> hits = operations.search(
                    query,
                    TouristSpotSearchDocument.class,
                    indexCoordinates
            );
            return new TouristSpotSearchResult(
                    hits.getSearchHits().stream().map(this::toEvidence).toList(),
                    hits.getTotalHits(),
                    hits.getExecutionDuration()
            );
        } catch (RuntimeException exception) {
            throw new TouristSpotSearchException(
                    "Unable to search tourist spots.",
                    exception,
                    isRetryable(exception)
            );
        }
    }

    private static boolean isRetryable(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof TransientDataAccessException
                    || current instanceof ConnectException
                    || current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    NativeQuery buildQuery(TouristSpotSearchCriteria criteria) {
        BoolQuery.Builder bool = new BoolQuery.Builder();
        if (criteria.hasKeyword()) {
            bool.must(keywordQuery(criteria.keyword()));
        } else {
            bool.must(Query.of(query -> query.matchAll(matchAll -> matchAll)));
        }

        List<Query> filters = filters(criteria);
        if (!filters.isEmpty()) {
            bool.filter(filters);
        }

        NativeQueryBuilder builder = NativeQuery.builder()
                .withQuery(Query.of(query -> query.bool(bool.build())))
                .withPageable(PageRequest.of(0, criteria.size()))
                .withSourceFilter(new FetchSourceFilterBuilder()
                        .withExcludes("sourceModifiedAt")
                        .build())
                .withTrackTotalHits(true);
        if (criteria.minimumScore() != null) {
            builder.withMinScore(criteria.minimumScore());
        }
        return builder.build();
    }

    private Query keywordQuery(String keyword) {
        Query multiMatch = Query.of(query -> query.multiMatch(multi -> multi
                .query(keyword)
                .fields(WEIGHTED_FIELDS)
                .type(TextQueryType.CrossFields)
                .minimumShouldMatch("75%")
        ));
        Query exactTitle = Query.of(query -> query.term(term -> term
                .field("title.keyword")
                .value(keyword)
                .boost(8.0f)
        ));
        return Query.of(query -> query.bool(bool -> bool
                .must(multiMatch)
                .should(exactTitle)
        ));
    }

    private List<Query> filters(TouristSpotSearchCriteria criteria) {
        List<Query> filters = new ArrayList<>();
        addLongTerm(filters, "region.regionId", criteria.regionId());
        addTerm(filters, "region.areaCode", criteria.areaCode());
        addTerm(filters, "region.sigunguCode", criteria.sigunguCode());
        addIntegerTerm(filters, "category.contentTypeId", criteria.contentTypeId());
        addLongTerm(filters, "category.categoryId", criteria.categoryId());
        addTerm(filters, "category.largeCategoryCode", criteria.largeCategoryCode());
        addTerm(filters, "category.middleCategoryCode", criteria.middleCategoryCode());
        addTerm(filters, "category.smallCategoryCode", criteria.smallCategoryCode());
        if (criteria.hasDistance()) {
            GeoLocation location = GeoLocation.of(geo -> geo.latlon(latlon -> latlon
                    .lat(criteria.latitude())
                    .lon(criteria.longitude())
            ));
            filters.add(Query.of(query -> query.geoDistance(geo -> geo
                    .field("coordinates")
                    .location(location)
                    .distance(criteria.radiusKm() + "km")
            )));
        }
        return filters;
    }

    private void addTerm(List<Query> filters, String field, String value) {
        if (value == null) {
            return;
        }
        filters.add(Query.of(query -> query.term(term -> term.field(field).value(value))));
    }

    private void addLongTerm(List<Query> filters, String field, Long value) {
        if (value != null) {
            filters.add(Query.of(query -> query.term(term -> term.field(field).value(value))));
        }
    }

    private void addIntegerTerm(List<Query> filters, String field, Integer value) {
        if (value != null) {
            filters.add(Query.of(query -> query.term(term -> term.field(field).value(value.longValue()))));
        }
    }

    private TouristSpotSearchEvidence toEvidence(SearchHit<TouristSpotSearchDocument> hit) {
        TouristSpotSearchDocument document = hit.getContent();
        GeoPoint coordinates = document.coordinates();
        return new TouristSpotSearchEvidence(
                document.touristSpotId(),
                document.title(),
                document.description(),
                document.address(),
                document.region() == null ? null : document.region().regionId(),
                document.category() == null ? null : document.category().categoryId(),
                document.category() == null ? null : document.category().contentTypeId(),
                coordinates == null ? null : coordinates.getLat(),
                coordinates == null ? null : coordinates.getLon(),
                document.imageUrl(),
                document.phoneNumber(),
                document.openingHours(),
                document.admissionFee(),
                document.reservationUrl(),
                hit.getScore()
        );
    }
}
