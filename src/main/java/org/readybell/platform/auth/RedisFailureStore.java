package org.readybell.platform.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.ZSetOperations;

/**
 * Failure log in Redis sorted sets (score = epoch millis), so every replica applies the same
 * limit. If Redis is unreachable it degrades to the per-node {@link InMemoryFailureStore} rather
 * than disabling the throttle.
 */
public class RedisFailureStore implements FailureStore {

    private static final Logger log = LoggerFactory.getLogger(RedisFailureStore.class);

    private final StringRedisTemplate redis;
    private final String prefix;
    private final InMemoryFailureStore fallback = new InMemoryFailureStore();

    public RedisFailureStore(StringRedisTemplate redis, String prefix) {
        this.redis = redis;
        this.prefix = prefix;
    }

    @Override
    public void add(String key, Instant now, Duration window) {
        fallback.add(key, now, window); // cheap; keeps the fallback warm
        try {
            String k = prefix + key;
            var zset = redis.opsForZSet();
            zset.removeRangeByScore(k, 0, now.minus(window).toEpochMilli());
            zset.add(k, now.toEpochMilli() + "-" + UUID.randomUUID(), now.toEpochMilli());
            redis.expire(k, window.plusSeconds(60));
        } catch (RuntimeException e) {
            log.warn("Redis login throttle unavailable, using per-node counters: {}", e.getMessage());
        }
    }

    @Override
    public Window window(String key, Instant now, Duration window) {
        try {
            String k = prefix + key;
            var zset = redis.opsForZSet();
            zset.removeRangeByScore(k, 0, now.minus(window).toEpochMilli());
            Long count = zset.zCard(k);
            if (count == null || count == 0) {
                return Window.EMPTY;
            }
            Set<ZSetOperations.TypedTuple<String>> first = zset.rangeWithScores(k, 0, 0);
            Optional<Instant> oldest = first == null ? Optional.empty() : first.stream().findFirst()
                    .map(t -> Instant.ofEpochMilli(t.getScore().longValue()));
            return new Window(count.intValue(), oldest);
        } catch (RuntimeException e) {
            log.warn("Redis login throttle unavailable, using per-node counters: {}", e.getMessage());
            return fallback.window(key, now, window);
        }
    }

    @Override
    public void clear(String key) {
        fallback.clear(key);
        try {
            redis.delete(prefix + key);
        } catch (RuntimeException e) {
            log.warn("Redis login throttle unavailable: {}", e.getMessage());
        }
    }
}
