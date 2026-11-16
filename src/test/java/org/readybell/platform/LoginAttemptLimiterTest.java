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
    /** 3 failures per account+address, 6 per address, 10 per account, 10 minute window. */
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(3, 6, 10, Duration.ofMinutes(10), clock,
            new org.readybell.platform.auth.InMemoryFailureStore());

    @Test
    void guessingFromOneAddressIsLockedAfterTheLimitAndReportsTheWait() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.retryAfter("bob", "1.1.1.1")).isZero();
            limiter.recordFailure("bob", "1.1.1.1");
        }
        assertThat(limiter.retryAfter("bob", "1.1.1.1")).isEqualTo(Duration.ofMinutes(10));
        clock.advance(Duration.ofMinutes(4));
        assertThat(limiter.retryAfter("bob", "1.1.1.1")).isEqualTo(Duration.ofMinutes(6));
    }

    @Test
    void aStrangerCannotLockTheOwnerOutFromAnotherAddress() {
        for (int i = 0; i < 3; i++) limiter.recordFailure("bob", "6.6.6.6");     // the attacker

        assertThat(limiter.retryAfter("bob", "6.6.6.6")).isPositive();           // the attacker is locked
        assertThat(limiter.retryAfter("bob", "7.7.7.7")).isZero();               // the owner, elsewhere, is not
    }

    @Test
    void failuresExpireAfterWindow() {
        for (int i = 0; i < 3; i++) limiter.recordFailure("bob", "1.1.1.1");
        clock.advance(Duration.ofMinutes(10));
        assertThat(limiter.retryAfter("bob", "1.1.1.1")).isZero();
    }

    @Test
    void anAddressTryingManyAccountsIsLockedForAll() {
        for (int i = 0; i < 6; i++) limiter.recordFailure("user" + i, "2.2.2.2");   // credential stuffing
        assertThat(limiter.retryAfter("brand-new-user", "2.2.2.2")).isPositive();
        assertThat(limiter.retryAfter("brand-new-user", "3.3.3.3")).isZero();
    }

    @Test
    void aDistributedGuessAtOneAccountLocksItOnlyAfterTheHighAccountLimit() {
        for (int i = 0; i < 9; i++) limiter.recordFailure("bob", "10.0.0." + i);    // 9 addresses, one failure each
        assertThat(limiter.retryAfter("bob", "10.9.9.9")).isZero();
        limiter.recordFailure("bob", "10.0.0.9");
        assertThat(limiter.retryAfter("bob", "10.9.9.9")).isPositive();               // 10th failure: the account is locked for everyone
    }

    @Test
    void successClearsTheCountersAndIsCaseInsensitive() {
        limiter.recordFailure("Bob", "4.4.4.4");
        limiter.recordFailure("bob", "4.4.4.4");
        limiter.recordFailure("BOB", "4.4.4.4");
        assertThat(limiter.retryAfter("bob", "4.4.4.4")).isPositive();
        limiter.recordSuccess("bob", "4.4.4.4");
        assertThat(limiter.retryAfter("bob", "4.4.4.4")).isZero();
    }
}
