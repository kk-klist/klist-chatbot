package com.klist.chatbot.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class MicrometerRetryEventListenerTest {

    @Test
    void recordsRetryAndExhaustionCountersWithBoundedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerRetryEventListener listener = new MicrometerRetryEventListener(registry);

        listener.retrying("openai", "timeout", 2, Duration.ofMillis(100));
        listener.retrying("openai", "timeout", 3, Duration.ofMillis(200));
        listener.exhausted("elasticsearch", "transient", 3);

        assertThat(registry.get("chatbot.retry.attempts")
                .tags("component", "openai", "reason", "timeout")
                .counter().count()).isEqualTo(2.0);
        assertThat(registry.get("chatbot.retry.exhausted")
                .tags("component", "elasticsearch", "reason", "transient")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void logsRetryAndExhaustionWithOperationalFields(CapturedOutput output) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerRetryEventListener listener = new MicrometerRetryEventListener(registry);

        listener.retrying("elasticsearch", "transient", 2, Duration.ofMillis(100));
        listener.exhausted("elasticsearch", "transient", 3);

        assertThat(output)
                .contains("External operation retry scheduled.")
                .contains("component=elasticsearch")
                .contains("reason=transient")
                .contains("nextAttempt=2")
                .contains("backoffMs=100")
                .contains("External operation retries exhausted.")
                .contains("attempts=3");
    }
}
