package org.readybell.platform.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Per-node failure log (the default; also the fallback when Redis is unreachable). */
public class InMemoryFailureStore implements FailureStore {

    private static final int MAX_TRACKED_KEYS = 10_000;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    @Override
    public void add(String key, Instant now, Duration window) {
        prune(now, window);
        Deque<Instant> q = failures.computeIfAbsent(key, x -> new ArrayDeque<>());
        synchronized (q) {
            evict(q, now, window);
            q.addLast(now);
        }
    }

    @Override
    public Window window(String key, Instant now, Duration window) {
        Deque<Instant> q = failures.get(key);
        if (q == null) {
            return Window.EMPTY;
        }
        synchronized (q) {
            evict(q, now, window);
            return new Window(q.size(), Optional.ofNullable(q.peekFirst()));
        }
    }

    @Override
    public void clear(String key) {
        failures.remove(key);
    }

    private static void evict(Deque<Instant> q, Instant now, Duration window) {
        Instant cutoff = now.minus(window);
        while (!q.isEmpty() && !q.peekFirst().isAfter(cutoff)) {
            q.pollFirst();
        }
    }

    private void prune(Instant now, Duration window) {
        if (failures.size() < MAX_TRACKED_KEYS) {
            return;
        }
        failures.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                evict(e.getValue(), now, window);
                return e.getValue().isEmpty();
            }
        });
    }
}
