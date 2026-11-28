package org.readybell.platform.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Revocation list shared by all replicas through Redis, so a sign-out on one node is honoured by the others. It writes to
 * the local list too and reads both, so a Redis outage degrades to per-node revocation instead of failing requests
 * (the alternative would log every staff member out whenever Redis blips).
 */
public class RedisTokenRevocationStore implements TokenRevocationStore {
    private static final Logger log = LoggerFactory.getLogger(RedisTokenRevocationStore.class);

    private final StringRedisTemplate redis;
    private final String prefix;
    private final InMemoryTokenRevocationStore local;

    public RedisTokenRevocationStore(StringRedisTemplate redis, String prefix, Clock clock) {
        this.redis = redis;
        this.prefix = prefix;
        this.local = new InMemoryTokenRevocationStore(clock);
    }

    @Override
    public void revokeSession(String sessionId, Duration keepFor) {
        local.revokeSession(sessionId, keepFor);
        try {
            redis.opsForValue().set(prefix + "sid:" + sessionId, "1", keepFor);
        } catch (RuntimeException e) {
            log.warn("Redis revocation list unavailable, revoked on this node only: {}", e.getMessage());
        }
    }

    @Override
    public boolean isSessionRevoked(String sessionId) {
        if (local.isSessionRevoked(sessionId)) {
            return true;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey(prefix + "sid:" + sessionId));
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public void revokeTokenAt(String tokenId, Instant at, Duration keepFor) {
        local.revokeTokenAt(tokenId, at, keepFor);
        try {
            redis.opsForValue().set(prefix + "jti:" + tokenId, String.valueOf(at.toEpochMilli()), keepFor);
        } catch (RuntimeException e) {
            log.warn("Redis revocation list unavailable, revoked on this node only: {}", e.getMessage());
        }
    }

    @Override
    public boolean isTokenRevoked(String tokenId, Instant now) {
        if (local.isTokenRevoked(tokenId, now)) {
            return true;
        }
        try {
            String at = redis.opsForValue().get(prefix + "jti:" + tokenId);
            return at != null && Instant.ofEpochMilli(Long.parseLong(at)).compareTo(now) <= 0;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
