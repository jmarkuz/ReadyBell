package org.readybell.platform.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A token proves who signed in, not that the account still exists. This asks the user store (cached for a short time, so a
 * request costs no query) whether the account is still enabled: a deleted staff member or a deactivated restaurant loses
 * access within the cache time instead of when the token expires.
 */
public class AccountGuard {

    private static final int MAX_ENTRIES = 10_000;

    private record Verdict(boolean active, Instant checkedAt) {}

    private final UserDetailsService users;
    private final Clock clock;
    private final Duration cacheFor;
    private final Map<String, Verdict> cache = new ConcurrentHashMap<>();

    public AccountGuard(UserDetailsService users, Clock clock, Duration cacheFor) {
        this.users = users;
        this.clock = clock;
        this.cacheFor = cacheFor;
    }

    public boolean isActive(String username) {
        Instant now = clock.instant();
        Verdict known = cache.get(username);
        if (known != null && known.checkedAt().plus(cacheFor).isAfter(now)) {
            return known.active();
        }
        boolean active;
        try {
            UserDetails details = users.loadUserByUsername(username);
            active = details.isEnabled();
        } catch (UsernameNotFoundException e) {
            active = false;
        }
        if (cache.size() >= MAX_ENTRIES) {
            cache.clear();
        }
        cache.put(username, new Verdict(active, now));
        return active;
    }
}
