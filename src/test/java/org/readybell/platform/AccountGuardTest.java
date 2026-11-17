package org.readybell.platform;

import org.junit.jupiter.api.Test;
import org.readybell.platform.auth.AccountGuard;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.readybell.shared.domain.TenantId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

    private static final TenantId TENANT = new TenantId(UUID.randomUUID());
    private static final TenantId OTHER = new TenantId(UUID.randomUUID());

    private final AccountGuard guard = new AccountGuard((tenant, username) -> {
        lookups.incrementAndGet();
        if (!exists) return Optional.empty();
        return Optional.of(new TenantUser(username, "x", enabled, List.of(new SimpleGrantedAuthority("ROLE_STAFF")), tenant));
    }, clock, Duration.ofSeconds(30));

    @Test
    void anActiveAccountPassesAndIsCachedSoRequestsCostNoQuery() {
        assertThat(guard.isActive(TENANT, "bob")).isTrue();
        assertThat(guard.isActive(TENANT, "bob")).isTrue();
        assertThat(guard.isActive(TENANT, "bob")).isTrue();

        assertThat(lookups.get()).isEqualTo(1);
    }

    @Test
    void aDeletedOrDeactivatedAccountLosesAccessWhenTheCacheExpires() {
        assertThat(guard.isActive(TENANT, "bob")).isTrue();
        exists = false;
        assertThat(guard.isActive(TENANT, "bob")).isTrue();                     // still within the cache time
        clock.now = clock.now.plusSeconds(31);
        assertThat(guard.isActive(TENANT, "bob")).isFalse();

        exists = true;
        enabled = false;                                                // a deactivated restaurant disables its accounts
        clock.now = clock.now.plusSeconds(31);
        assertThat(guard.isActive(TENANT, "bob")).isFalse();
    }

    @Test
    void theSameUsernameInAnotherRestaurantIsAnotherAccount() {
        assertThat(guard.isActive(TENANT, "admin")).isTrue();
        assertThat(guard.isActive(OTHER, "admin")).isTrue();

        assertThat(lookups.get()).isEqualTo(2);       // not served from the first restaurant's cached verdict
    }
}
