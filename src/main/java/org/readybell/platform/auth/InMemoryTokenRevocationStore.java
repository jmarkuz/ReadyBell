package org.readybell.platform.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Per-node revocation list; also the fallback of {@link RedisTokenRevocationStore}. */
public class InMemoryTokenRevocationStore implements TokenRevocationStore {

    private static final int MAX_ENTRIES = 50_000;

    private record Entry(Instant revokedAt, Instant forgetAt) {}

    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();
    private final Map<String, Entry> tokens = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryTokenRevocationStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void revokeSession(String sessionId, Duration keepFor) {
        put(sessions, sessionId, clock.instant(), keepFor);
    }

    @Override
    public boolean isSessionRevoked(String sessionId) {
        return effective(sessions.get(sessionId), clock.instant());
    }

    @Override
    public void revokeTokenAt(String tokenId, Instant at, Duration keepFor) {
        put(tokens, tokenId, at, keepFor);
    }

    @Override
    public boolean isTokenRevoked(String tokenId, Instant now) {
        return effective(tokens.get(tokenId), now);
    }

    private void put(Map<String, Entry> map, String key, Instant revokedAt, Duration keepFor) {
        if (map.size() >= MAX_ENTRIES) {
            Instant now = clock.instant();
            map.values().removeIf(e -> !e.forgetAt().isAfter(now));
        }
        map.put(key, new Entry(revokedAt, clock.instant().plus(keepFor)));
    }

    private boolean effective(Entry entry, Instant now) {
        return entry != null && entry.forgetAt().isAfter(clock.instant()) && !entry.revokedAt().isAfter(now);
    }
}
