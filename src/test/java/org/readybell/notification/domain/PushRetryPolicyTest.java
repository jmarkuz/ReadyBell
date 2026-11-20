package org.readybell.notification.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PushRetryPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");
    private final PushRetryPolicy policy = new PushRetryPolicy(List.of(
            Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(30)));

    @Test
    void theNthFailureIsFollowedByTheNthPause() {
        assertThat(policy.nextAttempt(1, NOW, null)).contains(NOW.plusSeconds(1));
        assertThat(policy.nextAttempt(2, NOW, null)).contains(NOW.plusSeconds(5));
        assertThat(policy.nextAttempt(3, NOW, null)).contains(NOW.plusSeconds(30));
    }

    @Test
    void afterTheLastPauseThereIsNoRetry() {
        assertThat(policy.nextAttempt(4, NOW, null)).isEmpty();
    }

    @Test
    void aRetryAfterTheDeadlineIsRefused() {
        assertThat(policy.nextAttempt(3, NOW, NOW.plusSeconds(29))).isEmpty();
        assertThat(policy.nextAttempt(3, NOW, NOW.plusSeconds(30))).contains(NOW.plusSeconds(30));
    }

    @Test
    void noPausesMeansNoRetries() {
        assertThat(new PushRetryPolicy(List.of()).nextAttempt(1, NOW, null)).isEmpty();
    }
}
