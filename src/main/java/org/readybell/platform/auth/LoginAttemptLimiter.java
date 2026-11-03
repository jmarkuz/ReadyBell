package org.readybell.platform.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window limiter for failed logins, keyed separately by client address and by username so
 * that neither password guessing against one account nor from one address can go unchecked.
 *
 * <p>State is in memory and therefore per node; with several replicas the effective limit is
 * {@code replicas x maxFailures}. A successful login clears the username's counter.
 */
public class LoginAttemptLimiter {

    private static final int MAX_TRACKED_KEYS = 10_000;

    private final int maxFailures;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginAttemptLimiter(int maxFailures, Duration window, Clock clock) {
        this.maxFailures = maxFailures;
        this.window = window;
        this.clock = clock;
    }

    /** @return how long the caller must wait, or {@link Duration#ZERO} if an attempt is allowed. */
    public Duration retryAfter(String username, String address) {
        Duration a = retryAfterKey(key("u", username));
        Duration b = retryAfterKey(key("a", address));
        return a.compareTo(b) >= 0 ? a : b;
    }

    public void recordFailure(String username, String address) {
        prune();
        Instant now = clock.instant();
        for (String k : new String[]{key("u", username), key("a", address)}) {
            Deque<Instant> q = failures.computeIfAbsent(k, x -> new ArrayDeque<>());
            synchronized (q) {
                evict(q, now);
                q.addLast(now);
            }
        }
    }

    public void recordSuccess(String username) {
        failures.remove(key("u", username));
    }

    private Duration retryAfterKey(String k) {
        Deque<Instant> q = failures.get(k);
        if (q == null) {
            return Duration.ZERO;
        }
        Instant now = clock.instant();
        synchronized (q) {
            evict(q, now);
            if (q.size() < maxFailures) {
                return Duration.ZERO;
            }
            Duration wait = Duration.between(now, q.peekFirst().plus(window));
            return wait.isNegative() ? Duration.ZERO : wait;
        }
    }

    private void evict(Deque<Instant> q, Instant now) {
        Instant cutoff = now.minus(window);
        while (!q.isEmpty() && !q.peekFirst().isAfter(cutoff)) {
            q.pollFirst();
        }
    }

    /** Bounds memory: drops expired keys once the map grows large. */
    private void prune() {
        if (failures.size() < MAX_TRACKED_KEYS) {
            return;
        }
        Instant now = clock.instant();
        failures.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                evict(e.getValue(), now);
                return e.getValue().isEmpty();
            }
        });
    }

    private static String key(String kind, String value) {
        return kind + ":" + (value == null ? "" : value.toLowerCase(Locale.ROOT));
    }
}
