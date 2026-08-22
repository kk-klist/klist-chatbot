package com.klist.chatbot.infrastructure.search.document;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.data.elasticsearch.annotations.GeoPointField;
import org.springframework.data.elasticsearch.annotations.WriteTypeHint;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;

@Document(indexName = "tourist-spots", createIndex = false, writeTypeHint = WriteTypeHint.FALSE)
public record TouristSpotSearchDocument(
        @Id
        Long touristSpotId,

        @MultiField(
                mainField = @Field(
                        type = FieldType.Text,
                        analyzer = "korean_index",
                        searchAnalyzer = "korean_search"
                ),
                otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword)
        )
        String title,

        @Field(type = FieldType.Text, analyzer = "korean_index", searchAnalyzer = "korean_search")
        String description,

        @MultiField(
                mainField = @Field(
                        type = FieldType.Text,
                        analyzer = "korean_index",
                        searchAnalyzer = "korean_search"
                ),
                otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword)
        )
        String address,

        @Field(type = FieldType.Object)
        TouristSpotSearchRegion region,

        @Field(type = FieldType.Object)
        TouristSpotSearchCategory category,

        @GeoPointField
        GeoPoint coordinates,

        @Field(type = FieldType.Keyword, index = false)
        String imageUrl,

        @Field(type = FieldType.Keyword)
        String phoneNumber,

        @Field(type = FieldType.Text, analyzer = "standard")
        String openingHours,

        @Field(type = FieldType.Text, analyzer = "standard")
        String admissionFee,

        @Field(type = FieldType.Keyword, index = false)
        String reservationUrl,

        @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
        LocalDateTime sourceModifiedAt
) {
}
