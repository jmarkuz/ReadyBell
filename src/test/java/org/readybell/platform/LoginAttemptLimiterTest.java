package org.readybell.platform;

import org.junit.jupiter.api.Test;
import org.readybell.platform.auth.LoginAttemptLimiter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptLimiterTest {

    /** Clock whose time the test advances. */
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(3, Duration.ofMinutes(10), clock);

    @Test
    void allowsAttemptsUntilLimitThenReportsWait() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.retryAfter("bob", "1.1.1." + i)).isZero();
            limiter.recordFailure("bob", "1.1.1." + i);
        }
        assertThat(limiter.retryAfter("bob", "9.9.9.9")).isEqualTo(Duration.ofMinutes(10));
        clock.advance(Duration.ofMinutes(4));
        assertThat(limiter.retryAfter("bob", "9.9.9.9")).isEqualTo(Duration.ofMinutes(6));
    }

    @Test
    void failuresExpireAfterWindow() {
        for (int i = 0; i < 3; i++) limiter.recordFailure("bob", "1.1.1.1");
        clock.advance(Duration.ofMinutes(10));
        assertThat(limiter.retryAfter("bob", "1.1.1.1")).isZero();
    }

    @Test
    void addressIsLimitedAcrossUsernames() {
        limiter.recordFailure("a", "2.2.2.2");
        limiter.recordFailure("b", "2.2.2.2");
        limiter.recordFailure("c", "2.2.2.2");
        assertThat(limiter.retryAfter("brand-new-user", "2.2.2.2")).isPositive();
        assertThat(limiter.retryAfter("brand-new-user", "3.3.3.3")).isZero();
    }

    @Test
    void successClearsUsernameCounterAndIsCaseInsensitive() {
        limiter.recordFailure("Bob", "4.4.4.4");
        limiter.recordFailure("bob", "5.5.5.5");
        limiter.recordFailure("BOB", "6.6.6.6");
        assertThat(limiter.retryAfter("bob", "7.7.7.7")).isPositive();
        limiter.recordSuccess("bob");
        assertThat(limiter.retryAfter("bob", "7.7.7.7")).isZero();
    }
}
