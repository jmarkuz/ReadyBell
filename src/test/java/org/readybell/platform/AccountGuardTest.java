package org.readybell.platform;

import org.junit.jupiter.api.Test;
import org.readybell.platform.auth.AccountGuard;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AccountGuardTest {

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MutableClock clock = new MutableClock();
    private final AtomicInteger lookups = new AtomicInteger();
    private volatile boolean exists = true;
    private volatile boolean enabled = true;

    private final AccountGuard guard = new AccountGuard(username -> {
        lookups.incrementAndGet();
        if (!exists) throw new UsernameNotFoundException(username);
        UserDetails u = User.withUsername(username).password("x").roles("STAFF").disabled(!enabled).build();
        return u;
    }, clock, Duration.ofSeconds(30));

    @Test
    void anActiveAccountPassesAndIsCachedSoRequestsCostNoQuery() {
        assertThat(guard.isActive("bob")).isTrue();
        assertThat(guard.isActive("bob")).isTrue();
        assertThat(guard.isActive("bob")).isTrue();

        assertThat(lookups.get()).isEqualTo(1);
    }

    @Test
    void aDeletedOrDeactivatedAccountLosesAccessWhenTheCacheExpires() {
        assertThat(guard.isActive("bob")).isTrue();
        exists = false;
        assertThat(guard.isActive("bob")).isTrue();                     // still within the cache time
        clock.now = clock.now.plusSeconds(31);
        assertThat(guard.isActive("bob")).isFalse();

        exists = true;
        enabled = false;                                                // a deactivated restaurant disables its accounts
        clock.now = clock.now.plusSeconds(31);
        assertThat(guard.isActive("bob")).isFalse();
    }
}
