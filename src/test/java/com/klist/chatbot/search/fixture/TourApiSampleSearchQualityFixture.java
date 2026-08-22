package com.klist.chatbot.search.fixture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.klist.chatbot.infrastructure.search.document.TouristSpotSearchCategory;
import com.klist.chatbot.infrastructure.search.document.TouristSpotSearchDocument;
import com.klist.chatbot.infrastructure.search.document.TouristSpotSearchRegion;
import com.klist.chatbot.search.application.TouristSpotSearchCriteria;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;

public final class TourApiSampleSearchQualityFixture {

    private static final String RESOURCE = "/tourapi/tour-api-normalization-fixture.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TourApiSampleSearchQualityFixture() {
    }

    public static SampleData load() {
        try (InputStream input = TourApiSampleSearchQualityFixture.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("TourAPI search quality fixture was not found.");
            }
            JsonNode root = OBJECT_MAPPER.readTree(input);
            List<TouristSpotSearchDocument> documents = new ArrayList<>();
            List<SearchQualityCase> cases = new ArrayList<>();
            for (JsonNode sample : root) {
                JsonNode list = sample.path("areaBasedList2");
                JsonNode common = sample.path("detailCommon2");
                long contentId = list.path("contentid").asLong();
                int contentTypeId = list.path("contenttypeid").asInt();
                String title = text(common, "title", text(list, "title", null));
                String description = text(common, "overview", null);
                String address = text(common, "addr1", text(list, "addr1", null));
                String openingHours = nullableText(sample, "expectedOpeningHours");
                String admissionFee = nullableText(sample, "expectedAdmissionFee");
                documents.add(new TouristSpotSearchDocument(
                        contentId,
                        title,
                        description,
                        address,
                        new TouristSpotSearchRegion(null, text(list, "areacode", null),
                                text(list, "sigungucode", null), null, null),
                        new TouristSpotSearchCategory(null, contentTypeId,
                                text(list, "cat1", null), text(list, "cat2", null), text(list, "cat3", null)),
                        new GeoPoint(list.path("mapy").asDouble(), list.path("mapx").asDouble()),
                        text(list, "firstimage", null),
                        text(common, "tel", null),
                        openingHours,
                        admissionFee,
                        nullableText(sample, "expectedReservationUrl"),
                        null
                ));
                String question = question(contentTypeId, title, openingHours, admissionFee);
                cases.add(new SearchQualityCase(
                        question,
                        contentId,
                        "서울",
                        contentTypeId,
                        criteria(question, contentTypeId)
                ));
            }
            return new SampleData(List.copyOf(documents), List.copyOf(cases));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load TourAPI search quality fixture.", exception);
        }
    }

    private static TouristSpotSearchCriteria criteria(String question, int contentTypeId) {
        return new TouristSpotSearchCriteria(
                question, null, "1", null, contentTypeId, null, null, null, null,
                null, null, null, 5, null
        );
    }

    private static String question(
            int contentTypeId,
            String title,
            String openingHours,
            String admissionFee
    ) {
        return switch (contentTypeId) {
            case 12 -> title + " Mountain Seoul";
            case 14 -> title + " Culture facility " + admissionFee;
            case 15 -> title + " Seasonal event Free";
            case 25 -> title + " Course overview";
            case 28 -> title + " Kayak " + admissionFee;
            case 32 -> title + " Hotel " + openingHours;
            case 38 -> title + " Shopping " + openingHours;
            case 39 -> title + " Restaurant " + openingHours;
            default -> title;
        };
    }

    private static String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String text(JsonNode node, String field, String fallback) {
        String value = nullableText(node, field);
        return value == null || value.isBlank() ? fallback : value;
    }

    public record SampleData(
            List<TouristSpotSearchDocument> documents,
            List<SearchQualityCase> cases
    ) {
    }
}
