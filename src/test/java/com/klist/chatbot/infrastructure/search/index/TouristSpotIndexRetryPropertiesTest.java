package com.klist.chatbot.infrastructure.search.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class TouristSpotIndexRetryPropertiesTest {

    @Test
    void providesBoundedRetryDefaults() {
        TouristSpotIndexProperties properties = validProperties();

        properties.validate();

        assertThat(properties.getRetryMaxAttempts()).isEqualTo(3);
        assertThat(properties.getRetryInitialBackoff()).isEqualTo(Duration.ofMillis(100));
        assertThat(properties.getRetryMaxBackoff()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void rejectsInvalidRetryConfiguration() {
        TouristSpotIndexProperties properties = validProperties();
        properties.setRetryMaxAttempts(0);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("retry max attempts");

        properties.setRetryMaxAttempts(3);
        properties.setRetryInitialBackoff(Duration.ofSeconds(2));
        properties.setRetryMaxBackoff(Duration.ofSeconds(1));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("initial backoff");
    }

    private static TouristSpotIndexProperties validProperties() {
        TouristSpotIndexProperties properties = new TouristSpotIndexProperties();
        properties.setSettingsLocation(
                new ClassPathResource("elasticsearch/tourist-spots-settings.json")
        );
        properties.setMappingsLocation(
                new ClassPathResource("elasticsearch/tourist-spots-mappings.json")
        );
        return properties;
    }
}
