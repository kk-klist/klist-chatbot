package com.klist.chatbot.infrastructure.tourapi.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.klist.chatbot.infrastructure.tourapi.collector.TourApiCollectRequest;
import com.klist.chatbot.infrastructure.tourapi.dto.TourApiAreaBasedListItem;
import com.klist.chatbot.infrastructure.tourapi.dto.TourApiCodeItem;
import com.klist.chatbot.infrastructure.tourapi.dto.TourApiDetailCommonItem;
import com.klist.chatbot.infrastructure.tourapi.dto.TourApiDetailImageItem;
import com.klist.chatbot.infrastructure.tourapi.dto.TourApiDetailIntroItem;
import com.klist.chatbot.infrastructure.tourapi.dto.TourApiResponse;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

public class RestTourApiClient implements TourApiClient {

    private static final String SUCCESS_CODE = "0000";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final TourApiProperties properties;

    public RestTourApiClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            TourApiProperties properties
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public TourApiClientResult<TourApiPage<TourApiAreaBasedListItem>> getAreaBasedListPage(
            int pageNo,
            int numOfRows
    ) {
        if (pageNo < 1 || numOfRows < 1) {
            return invalidInput("pageNo and numOfRows must be positive");
        }
        String configurationError = configurationError();
        if (configurationError != null) {
            return TourApiClientResult.failure("configuration: " + configurationError);
        }

        try {
            URI uri = createUri("areaBasedList2", builder -> builder
                    .queryParam("pageNo", pageNo)
                    .queryParam("numOfRows", numOfRows));
            String json = restClient.get().uri(uri).retrieve().body(String.class);
            if (isBlank(json)) {
                return TourApiClientResult.empty("empty response");
            }
            return deserializePage(json);
        } catch (HttpStatusCodeException exception) {
            return TourApiClientResult.failure("http error: status " + exception.getStatusCode().value());
        } catch (ResourceAccessException exception) {
            if (hasCause(exception, SocketTimeoutException.class)
                    || hasCause(exception, HttpTimeoutException.class)) {
                return TourApiClientResult.failure("timeout: TourAPI request timed out");
            }
            if (hasCause(exception, ConnectException.class)) {
                return TourApiClientResult.failure("connection: unable to connect to TourAPI");
            }
            return TourApiClientResult.failure("connection: TourAPI request failed");
        } catch (IllegalArgumentException exception) {
            return TourApiClientResult.failure("configuration: invalid TourAPI base URL");
        }
    }

    @Override
    public TourApiClientResult<TourApiAreaBasedListItem> getAreaBasedList(TourApiCollectRequest request) {
        if (request == null || isBlank(request.contentId())) {
            return invalidInput("contentId is required");
        }
        return get("areaBasedList2", TourApiAreaBasedListItem.class,
                builder -> builder.queryParam("contentId", request.contentId()), item -> true);
    }

    @Override
    public TourApiClientResult<TourApiDetailCommonItem> getDetailCommon(String contentId) {
        if (isBlank(contentId)) {
            return invalidInput("contentId is required");
        }
        return get("detailCommon2", TourApiDetailCommonItem.class,
                builder -> builder.queryParam("contentId", contentId), item -> true);
    }

    @Override
    public TourApiClientResult<TourApiDetailIntroItem> getDetailIntro(
            String contentId,
            String contentTypeId
    ) {
        if (isBlank(contentId) || isBlank(contentTypeId)) {
            return invalidInput("contentId and contentTypeId are required");
        }
        return get("detailIntro2", TourApiDetailIntroItem.class,
                builder -> builder.queryParam("contentId", contentId)
                        .queryParam("contentTypeId", contentTypeId), item -> true);
    }

    @Override
    public TourApiClientResult<TourApiDetailImageItem> getDetailImage(String contentId) {
        if (isBlank(contentId)) {
            return invalidInput("contentId is required");
        }
        return get("detailImage2", TourApiDetailImageItem.class,
                builder -> builder.queryParam("contentId", contentId)
                        .queryParam("imageYN", "Y")
                        .queryParam("subImageYN", "Y"), item -> true);
    }

    @Override
    public TourApiClientResult<TourApiCodeItem> getRegionCode(String areaCode, String sigunguCode) {
        if (isBlank(areaCode) || isBlank(sigunguCode)) {
            return invalidInput("areaCode and sigunguCode are required");
        }
        return get("areaCode2", TourApiCodeItem.class,
                builder -> builder.queryParam("areaCode", areaCode)
                        .queryParam("numOfRows", 100),
                item -> sigunguCode.equals(item.code()));
    }

    private <T> TourApiClientResult<T> get(
            String endpoint,
            Class<T> itemType,
            UriCustomizer customizer,
            Predicate<T> selector
    ) {
        String configurationError = configurationError();
        if (configurationError != null) {
            return TourApiClientResult.failure("configuration: " + configurationError);
        }

        try {
            URI uri = createUri(endpoint, customizer);
            String json = restClient.get().uri(uri).retrieve().body(String.class);
            if (isBlank(json)) {
                return TourApiClientResult.empty("empty response");
            }
            return deserialize(json, itemType, selector);
        } catch (HttpStatusCodeException exception) {
            HttpStatusCode status = exception.getStatusCode();
            return TourApiClientResult.failure("http error: status " + status.value());
        } catch (ResourceAccessException exception) {
            if (hasCause(exception, SocketTimeoutException.class)
                    || hasCause(exception, HttpTimeoutException.class)) {
                return TourApiClientResult.failure("timeout: TourAPI request timed out");
            }
            if (hasCause(exception, ConnectException.class)) {
                return TourApiClientResult.failure("connection: unable to connect to TourAPI");
            }
            return TourApiClientResult.failure("connection: TourAPI request failed");
        } catch (IllegalArgumentException exception) {
            return TourApiClientResult.failure("configuration: invalid TourAPI base URL");
        }
    }

    private <T> TourApiClientResult<T> deserialize(
            String json,
            Class<T> itemType,
        Predicate<T> selector
    ) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode responseNode = root == null ? null : root.get("response");
            JsonNode headerNode = responseNode == null ? null : responseNode.get("header");
            if (headerNode == null || headerNode.isNull()) {
                return TourApiClientResult.failure("invalid response: response header is missing");
            }
            String resultCode = text(headerNode.get("resultCode"));
            String resultMsg = text(headerNode.get("resultMsg"));
            if (!SUCCESS_CODE.equals(resultCode)) {
                return TourApiClientResult.failure(
                        "invalid response: TourAPI resultCode=" + safe(resultCode)
                                + ", resultMsg=" + safe(resultMsg)
                );
            }
            if (!responseNode.hasNonNull("body")) {
                return TourApiClientResult.failure("invalid response: response body is missing");
            }

            JavaType type = objectMapper.getTypeFactory()
                    .constructParametricType(TourApiResponse.class, itemType);
            TourApiResponse<T> result = objectMapper.readValue(json, type);
            TourApiResponse.Items<T> items = result.response().body().items();
            List<T> values = items == null ? null : items.item();
            if (values == null || values.isEmpty()) {
                return TourApiClientResult.empty("TourAPI items are empty");
            }
            return values.stream()
                    .filter(selector)
                    .findFirst()
                    .map(TourApiClientResult::success)
                    .orElseGet(() -> TourApiClientResult.empty("matching TourAPI item is empty"));
        } catch (JsonProcessingException exception) {
            return TourApiClientResult.failure("json parsing: invalid TourAPI JSON");
        }
    }

    private TourApiClientResult<TourApiPage<TourApiAreaBasedListItem>> deserializePage(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode response = root == null ? null : root.get("response");
            JsonNode header = response == null ? null : response.get("header");
            if (header == null || header.isNull()) {
                return TourApiClientResult.failure("invalid response: response header is missing");
            }
            String resultCode = text(header.get("resultCode"));
            if (!SUCCESS_CODE.equals(resultCode)) {
                return TourApiClientResult.failure(
                        "invalid response: TourAPI resultCode=" + safe(resultCode)
                                + ", resultMsg=" + safe(text(header.get("resultMsg")))
                );
            }
            JsonNode body = response.get("body");
            if (body == null || !body.isObject()
                    || !body.has("pageNo") || !body.has("numOfRows") || !body.has("totalCount")) {
                return TourApiClientResult.failure("invalid response: pagination body is missing");
            }
            int pageNo = body.get("pageNo").asInt(-1);
            int numOfRows = body.get("numOfRows").asInt(-1);
            int totalCount = body.get("totalCount").asInt(-1);
            if (pageNo < 1 || numOfRows < 1 || totalCount < 0) {
                return TourApiClientResult.failure("invalid response: pagination values are invalid");
            }

            List<TourApiAreaBasedListItem> items = new ArrayList<>();
            JsonNode itemsNode = body.get("items");
            JsonNode itemNode = itemsNode != null && itemsNode.isObject() ? itemsNode.get("item") : null;
            if (itemNode != null && itemNode.isArray()) {
                for (JsonNode node : itemNode) {
                    items.add(objectMapper.treeToValue(node, TourApiAreaBasedListItem.class));
                }
            } else if (itemNode != null && itemNode.isObject()) {
                items.add(objectMapper.treeToValue(itemNode, TourApiAreaBasedListItem.class));
            }
            return TourApiClientResult.success(new TourApiPage<>(
                    items, pageNo, numOfRows, totalCount
            ));
        } catch (JsonProcessingException exception) {
            return TourApiClientResult.failure("json parsing: invalid TourAPI JSON");
        }
    }

    private URI createUri(String endpoint, UriCustomizer customizer) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(properties.getBaseUrl())
                .pathSegment(endpoint)
                .queryParam("serviceKey", properties.getServiceKey())
                .queryParam("MobileOS", properties.getMobileOs())
                .queryParam("MobileApp", properties.getMobileApp())
                .queryParam("_type", properties.getResponseType());
        customizer.customize(builder);
        return builder.build().encode().toUri();
    }

    private String configurationError() {
        if (isBlank(properties.getBaseUrl())) {
            return "base-url is required";
        }
        if (isBlank(properties.getServiceKey())) {
            return "service-key is required";
        }
        if (isBlank(properties.getMobileOs()) || isBlank(properties.getMobileApp())
                || isBlank(properties.getResponseType())) {
            return "common request parameters are required";
        }
        if (properties.getConnectTimeout() == null || properties.getConnectTimeout().isNegative()
                || properties.getConnectTimeout().isZero()
                || properties.getResponseTimeout() == null || properties.getResponseTimeout().isNegative()
                || properties.getResponseTimeout().isZero()) {
            return "timeouts must be positive";
        }
        return null;
    }

    private static <T> TourApiClientResult<T> invalidInput(String message) {
        return TourApiClientResult.failure("invalid request: " + message);
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String safe(String value) {
        return isBlank(value) ? "unknown" : value;
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @FunctionalInterface
    private interface UriCustomizer {
        void customize(UriComponentsBuilder builder);
    }
}
