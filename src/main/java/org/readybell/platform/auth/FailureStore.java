package org.readybell.platform.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Sliding-window failure log used by {@link LoginAttemptLimiter}; shared between nodes when backed by Redis. */
public interface FailureStore {

    /** Records a failure at {@code now} (and forgets failures older than {@code window}). */
    void add(String key, Instant now, Duration window);

    /** Failures inside the window. */
    Window window(String key, Instant now, Duration window);

    void clear(String key);

    /** Number of failures in the window and the time of the oldest one. */
    record Window(int count, Optional<Instant> oldest) {
        static final Window EMPTY = new Window(0, Optional.empty());
    }
}
