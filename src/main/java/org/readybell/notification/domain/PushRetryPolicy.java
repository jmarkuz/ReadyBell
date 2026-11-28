package org.readybell.notification.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * When to try a push again after a transient failure: the n-th retry waits {@code delays.get(n - 1)}; with no delay left,
 * or when the retry would fall after the deadline, there is none. Pure arithmetic: nothing here waits.
 */
public record PushRetryPolicy(List<Duration> delays) {
    public PushRetryPolicy {
        delays = List.copyOf(delays);
    }

    public Optional<Instant> nextAttempt(int attemptsMade, Instant now, Instant expiresAt) {
        if (attemptsMade < 1 || attemptsMade > delays.size()) {
            return Optional.empty();
        }
        Instant at = now.plus(delays.get(attemptsMade - 1));
        return expiresAt != null && at.isAfter(expiresAt) ? Optional.empty() : Optional.of(at);
    }
}
