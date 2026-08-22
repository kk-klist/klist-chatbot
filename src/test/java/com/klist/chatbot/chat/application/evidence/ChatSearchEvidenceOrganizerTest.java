package com.klist.chatbot.chat.application.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.klist.chatbot.search.application.TouristSpotSearchEvidence;
import com.klist.chatbot.search.application.TouristSpotSearchResult;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatSearchEvidenceOrganizerTest {

    private final ChatSearchEvidenceOrganizer organizer = new ChatSearchEvidenceOrganizer();

    @Test
    void organizesSearchFactsAndMetadataForChatGrounding() {
        TouristSpotSearchResult searchResult = new TouristSpotSearchResult(
                List.of(evidence()),
                12,
                Duration.ofMillis(18)
        );

        ChatEvidenceContext context = organizer.organize(searchResult);

        assertThat(context.totalHits()).isEqualTo(12);
        assertThat(context.searchExecutionTime()).isEqualTo(Duration.ofMillis(18));
        assertThat(context.touristSpots()).singleElement().satisfies(evidence -> {
            assertThat(evidence.touristSpotId()).isEqualTo(1001L);
            assertThat(evidence.title()).isEqualTo("서울 전망대");
            assertThat(evidence.description()).isEqualTo("서울 야경을 볼 수 있는 전망 명소");
            assertThat(evidence.address()).isEqualTo("서울특별시 중구");
            assertThat(evidence.contentTypeId()).isEqualTo(12);
            assertThat(evidence.latitude()).isEqualTo(37.5512);
            assertThat(evidence.longitude()).isEqualTo(126.9882);
            assertThat(evidence.openingHours()).isEqualTo("10:00~22:00");
            assertThat(evidence.admissionFee()).isEqualTo("성인 10,000원");
            assertThat(evidence.reservationUrl()).isEqualTo("https://example.com/reservations");
            assertThat(evidence.searchScore()).isEqualTo(4.2f);
        });
    }

    @Test
    void removesBlankOptionalFactsAndPreservesEmptySearchResult() {
        TouristSpotSearchEvidence evidence = new TouristSpotSearchEvidence(
                1002L, "  해변  ", " ", null, null, null, null,
                null, null, null, " ", null, "", null, 1.0f
        );

        ChatEvidenceContext context = organizer.organize(new TouristSpotSearchResult(
                List.of(evidence),
                1,
                null
        ));

        assertThat(context.touristSpots()).singleElement().satisfies(organized -> {
            assertThat(organized.title()).isEqualTo("해변");
            assertThat(organized.description()).isNull();
            assertThat(organized.openingHours()).isNull();
            assertThat(organized.reservationUrl()).isNull();
        });

        ChatEvidenceContext empty = organizer.organize(new TouristSpotSearchResult(
                List.of(),
                0,
                null
        ));
        assertThat(empty.isEmpty()).isTrue();
        assertThat(empty.searchExecutionTime()).isZero();
    }

    @Test
    void rejectsNullSearchResult() {
        assertThatNullPointerException()
                .isThrownBy(() -> organizer.organize(null))
                .withMessage("searchResult must not be null");
    }

    private static TouristSpotSearchEvidence evidence() {
        return new TouristSpotSearchEvidence(
                1001L,
                "서울 전망대",
                "서울 야경을 볼 수 있는 전망 명소",
                "서울특별시 중구",
                11L,
                21L,
                12,
                37.5512,
                126.9882,
                "https://example.com/image.jpg",
                "02-1234-5678",
                "10:00~22:00",
                "성인 10,000원",
                "https://example.com/reservations",
                4.2f
        );
    }
}
