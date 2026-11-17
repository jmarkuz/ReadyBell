package org.readybell.platform.auth;

import org.readybell.platform.TenantUser;
import org.readybell.shared.domain.TenantId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A token proves who signed in, not that the account still exists. This asks the user store (cached for a short time, so a
 * request costs no query) whether the account is still enabled: a deleted staff member or a deactivated restaurant loses
 * access within the cache time instead of when the token expires.
 */
public class AccountGuard {

    private static final int MAX_ENTRIES = 10_000;

    private record Verdict(boolean active, Instant checkedAt) {}

    private final AccountLookup users;
    private final Clock clock;
    private final Duration cacheFor;
    private final Map<String, Verdict> cache = new ConcurrentHashMap<>();

    public AccountGuard(AccountLookup users, Clock clock, Duration cacheFor) {
        this.users = users;
        this.clock = clock;
        this.cacheFor = cacheFor;
    }

    /** @param tenant the token's tenant ({@code null} for the platform operator): a username is unique per tenant only */
    public boolean isActive(TenantId tenant, String username) {
        String key = (tenant == null ? "" : tenant.value().toString()) + "/" + username;
        Instant now = clock.instant();
        Verdict known = cache.get(key);
        if (known != null && known.checkedAt().plus(cacheFor).isAfter(now)) {
            return known.active();
        }
        Optional<TenantUser> account = users.find(tenant, username);
        boolean active = account.map(TenantUser::isEnabled).orElse(false);
        if (cache.size() >= MAX_ENTRIES) {
            cache.clear();
        }
        cache.put(key, new Verdict(active, now));
        return active;
    }
}
