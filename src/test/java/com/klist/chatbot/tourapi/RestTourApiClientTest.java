package com.klist.chatbot.tourapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klist.chatbot.infrastructure.tourapi.client.RestTourApiClient;
import com.klist.chatbot.infrastructure.tourapi.client.TourApiClientResult;
import com.klist.chatbot.infrastructure.tourapi.client.TourApiClientStatus;
import com.klist.chatbot.infrastructure.tourapi.client.TourApiProperties;
import com.klist.chatbot.infrastructure.tourapi.collector.TourApiCollectRequest;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestTourApiClientTest {

    private static final String BASE_URL = "https://tour-api.test";
    private static final String SUCCESS_PREFIX =
            """
            {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":{"item":[
            """;
    private static final String SUCCESS_SUFFIX = "]},\"numOfRows\":1,\"pageNo\":1,\"totalCount\":1}}}";

    private TourApiProperties properties;
    private MockRestServiceServer server;
    private RestTourApiClient client;

    @BeforeEach
    void setUp() {
        properties = properties();
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestTourApiClient(builder.build(), new ObjectMapper(), properties);
    }

    @Test
    void areaBasedListSuccess() {
        expectSuccess("/areaBasedList2", "{\"contentid\":\"126480\",\"title\":\"Namsan\"}");

        var result = client.getAreaBasedList(new TourApiCollectRequest("126480"));

        assertThat(result.status()).isEqualTo(TourApiClientStatus.SUCCESS);
        assertThat(result.value().contentid()).isEqualTo("126480");
        server.verify();
    }

    @Test
    void areaBasedListPageSuccess() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/areaBasedList2")))
                .andExpect(queryParam("pageNo", "2"))
                .andExpect(queryParam("numOfRows", "10"))
                .andRespond(withSuccess(
                        SUCCESS_PREFIX + "{\"contentid\":\"126480\"}"
                                + "]},\"numOfRows\":10,\"pageNo\":2,\"totalCount\":21}}}",
                        MediaType.APPLICATION_JSON));

        var result = client.getAreaBasedListPage(2, 10);

        assertThat(result.value().items()).hasSize(1);
        assertThat(result.value().pageNo()).isEqualTo(2);
        assertThat(result.value().hasNext()).isTrue();
        server.verify();
    }

    @Test
    void detailCommonSuccess() {
        expectSuccess("/detailCommon2", "{\"contentid\":\"126480\",\"overview\":\"overview\"}");

        var result = client.getDetailCommon("126480");

        assertThat(result.value().overview()).isEqualTo("overview");
        server.verify();
    }

    @Test
    void detailIntroSuccess() {
        expectSuccess("/detailIntro2", "{\"contentid\":\"126480\",\"contenttypeid\":\"12\"}");

        var result = client.getDetailIntro("126480", "12");

        assertThat(result.status()).isEqualTo(TourApiClientStatus.SUCCESS);
        server.verify();
    }

    @Test
    void detailImageSuccess() {
        expectSuccess("/detailImage2", "{\"contentid\":\"126480\",\"originimgurl\":\"image.jpg\"}");

        var result = client.getDetailImage("126480");

        assertThat(result.value().originimgurl()).isEqualTo("image.jpg");
        server.verify();
    }

    @Test
    void areaCodeSuccess() {
        expectSuccess("/areaCode2",
                "{\"code\":\"001\",\"name\":\"other\"},{\"code\":\"005\",\"name\":\"Gwanak-gu\"}");

        var result = client.getRegionCode("1", "005");

        assertThat(result.value().name()).isEqualTo("Gwanak-gu");
        server.verify();
    }

    @Test
    void http500() {
        expectStatus("/detailCommon2", HttpStatus.INTERNAL_SERVER_ERROR);

        var result = client.getDetailCommon("126480");

        assertFailure(result, "http error: status 500");
    }

    @Test
    void http404() {
        expectStatus("/detailImage2", HttpStatus.NOT_FOUND);

        var result = client.getDetailImage("126480");

        assertFailure(result, "http error: status 404");
    }

    @Test
    void timeout() {
        RestClient timeoutRestClient = RestClient.builder()
                .requestFactory((uri, method) -> {
                    throw new SocketTimeoutException("simulated timeout");
                })
                .build();
        client = new RestTourApiClient(timeoutRestClient, new ObjectMapper(), properties);

        var result = client.getDetailCommon("126480");

        assertFailure(result, "timeout:");
    }

    @Test
    void emptyResponse() {
        server.expect(once(), requestTo(BASE_URL + "/detailCommon2?serviceKey=test-key&MobileOS=ETC"
                        + "&MobileApp=test-app&_type=json&contentId=126480"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        var result = client.getDetailCommon("126480");

        assertThat(result.status()).isEqualTo(TourApiClientStatus.EMPTY);
        server.verify();
    }

    @Test
    void resultCodeFailure() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/detailCommon2")))
                .andRespond(withSuccess(
                        "{\"response\":{\"header\":{\"resultCode\":\"30\",\"resultMsg\":\"SERVICE ERROR\"},"
                                + "\"body\":{\"items\":\"\"}}}",
                        MediaType.APPLICATION_JSON));

        var result = client.getDetailCommon("126480");

        assertFailure(result, "resultCode=30");
        assertThat(result.reason()).doesNotContain("test-key");
        server.verify();
    }

    @Test
    void jsonError() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/detailCommon2")))
                .andRespond(withSuccess("{broken", MediaType.APPLICATION_JSON));

        var result = client.getDetailCommon("126480");

        assertFailure(result, "json parsing:");
        server.verify();
    }

    private void expectSuccess(String path, String items) {
        server.expect(requestTo(org.hamcrest.Matchers.containsString(path)))
                .andExpect(queryParam("serviceKey", "test-key"))
                .andExpect(queryParam("MobileOS", "ETC"))
                .andExpect(queryParam("MobileApp", "test-app"))
                .andExpect(queryParam("_type", "json"))
                .andRespond(withSuccess(SUCCESS_PREFIX + items + SUCCESS_SUFFIX, MediaType.APPLICATION_JSON));
    }

    private void expectStatus(String path, HttpStatus status) {
        server.expect(requestTo(org.hamcrest.Matchers.containsString(path)))
                .andRespond(withStatus(status));
    }

    private static TourApiProperties properties() {
        TourApiProperties properties = new TourApiProperties();
        properties.setBaseUrl(BASE_URL);
        properties.setServiceKey("test-key");
        properties.setMobileApp("test-app");
        return properties;
    }

    private static void assertFailure(TourApiClientResult<?> result, String reasonFragment) {
        assertThat(result.status()).isEqualTo(TourApiClientStatus.FAILURE);
        assertThat(result.reason()).contains(reasonFragment);
    }
}
