package org.readybell.platform.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Failed-login throttling with three counters, so that locking someone out is hard but guessing stays slow:
 * <ul>
 *   <li><b>account + address</b> ({@code maxFailures}, default 5 in the window): the usual guess limit. It locks only the
 *       combination, so a stranger who knows a username cannot lock the real owner out from another address;</li>
 *   <li><b>address</b> ({@code maxFailuresPerAddress}, default 30): one address trying many accounts (credential stuffing);</li>
 *   <li><b>account</b> ({@code maxFailuresPerAccount}, default 100): a distributed guess at one account. It is high on
 *       purpose: it is the only counter an attacker with many addresses could use to lock an account out.</li>
 * </ul>
 * A successful login clears the account + address and the account counters.
 */
public class LoginAttemptLimiter {
    private final int maxFailures;
    private final int maxFailuresPerAddress;
    private final int maxFailuresPerAccount;
    private final Duration window;
    private final Clock clock;
    private final FailureStore store;

    public LoginAttemptLimiter(int maxFailures, int maxFailuresPerAddress, int maxFailuresPerAccount, Duration window,
                               Clock clock, FailureStore store) {
        this.maxFailures = maxFailures;
        this.maxFailuresPerAddress = maxFailuresPerAddress;
        this.maxFailuresPerAccount = maxFailuresPerAccount;
        this.window = window;
        this.clock = clock;
        this.store = store;
    }

    /** How long this account must wait before logging in from this address, or {@link Duration#ZERO}. */
    public Duration retryAfter(String username, String address) {
        Duration wait = retryAfterKey(key("pair", username + "|" + address), maxFailures);
        wait = longer(wait, retryAfterKey(key("a", address), maxFailuresPerAddress));
        return longer(wait, retryAfterKey(key("u", username), maxFailuresPerAccount));
    }

    public void recordFailure(String username, String address) {
        Instant now = clock.instant();
        store.add(key("pair", username + "|" + address), now, window);
        store.add(key("a", address), now, window);
        store.add(key("u", username), now, window);
    }

    public void recordSuccess(String username, String address) {
        store.clear(key("pair", username + "|" + address));
        store.clear(key("u", username));
    }

    private Duration retryAfterKey(String k, int limit) {
        Instant now = clock.instant();
        FailureStore.Window w = store.window(k, now, window);
        if (w.count() < limit || w.oldest().isEmpty()) {
            return Duration.ZERO;
        }
        Duration wait = Duration.between(now, w.oldest().get().plus(window));
        return wait.isNegative() ? Duration.ZERO : wait;
    }

    private static Duration longer(Duration a, Duration b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    private static String key(String kind, String value) {
        return kind + ":" + (value == null ? "" : value.toLowerCase(Locale.ROOT));
    }
}
