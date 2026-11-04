package org.readybell.platform.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Sliding-window limiter for failed logins, keyed separately by client address and by username so
 * that neither password guessing against one account nor from one address can go unchecked.
 *
 * <p>State is in memory and therefore per node; with several replicas the effective limit is
 * {@code replicas x maxFailures}. A successful login clears the username's counter.
 */
public class LoginAttemptLimiter {

    private final int maxFailures;
    private final Duration window;
    private final Clock clock;
    private final FailureStore store;

    public LoginAttemptLimiter(int maxFailures, Duration window, Clock clock) {
        this(maxFailures, window, clock, new InMemoryFailureStore());
    }

    public LoginAttemptLimiter(int maxFailures, Duration window, Clock clock, FailureStore store) {
        this.maxFailures = maxFailures;
        this.window = window;
        this.clock = clock;
        this.store = store;
    }

    /**
     * How long the caller must wait before another attempt is allowed; {@link Duration#ZERO} when
     * not throttled. The longer of the per-username and per-address waits applies.
     */
    public Duration retryAfter(String username, String address) {
        Duration a = retryAfterKey(key("u", username));
        Duration b = retryAfterKey(key("a", address));
        return a.compareTo(b) >= 0 ? a : b;
    }

    /** Records a failed attempt against both the username and the client address. */
    public void recordFailure(String username, String address) {
        Instant now = clock.instant();
        store.add(key("u", username), now, window);
        store.add(key("a", address), now, window);
    }

    /** A successful sign-in clears the username's failures (the address keeps its history). */
    public void recordSuccess(String username) {
        store.clear(key("u", username));
    }

    private Duration retryAfterKey(String k) {
        Instant now = clock.instant();
        FailureStore.Window w = store.window(k, now, window);
        if (w.count() < maxFailures || w.oldest().isEmpty()) {
            return Duration.ZERO;
        }
        Duration wait = Duration.between(now, w.oldest().get().plus(window));
        return wait.isNegative() ? Duration.ZERO : wait;
    }

    private static String key(String kind, String value) {
        return kind + ":" + (value == null ? "" : value.toLowerCase(Locale.ROOT));
    }
}
