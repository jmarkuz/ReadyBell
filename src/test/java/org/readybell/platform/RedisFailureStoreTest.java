package org.readybell.platform;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.readybell.platform.auth.FailureStore;
import org.readybell.platform.auth.InMemoryFailureStore;
import org.readybell.platform.auth.LoginAttemptLimiter;
import org.readybell.platform.auth.RedisFailureStore;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Needs a Redis on localhost:6379; skipped when none is reachable. */
class RedisFailureStoreTest {

    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private final String prefix = "readybell-test:" + UUID.randomUUID() + ":";
    private final Instant t0 = Instant.parse("2026-01-01T00:00:00Z");

    @BeforeEach
    void connect() {
        factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", 6379));
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        boolean up;
        try { up = "PONG".equalsIgnoreCase(redis.getConnectionFactory().getConnection().ping()); }
        catch (Exception e) { up = false; }
        assumeTrue(up, "Redis not available");
    }

    @AfterEach
    void cleanup() {
        if (redis != null) {
            try { var keys = redis.keys(prefix + "*"); if (keys != null && !keys.isEmpty()) redis.delete(keys); } catch (Exception ignored) { }
        }
        if (factory != null) factory.destroy();
    }

    @Test
    void twoNodesShareOneLimit() {
        Clock clock = Clock.fixed(t0, ZoneOffset.UTC);
        var nodeA = new LoginAttemptLimiter(3, Duration.ofMinutes(10), clock, new RedisFailureStore(redis, prefix));
        var nodeB = new LoginAttemptLimiter(3, Duration.ofMinutes(10), clock, new RedisFailureStore(redis, prefix));

        nodeA.recordFailure("anna", "1.1.1.1");
        nodeB.recordFailure("anna", "1.1.1.1");
        assertEquals(Duration.ZERO, nodeA.retryAfter("anna", "9.9.9.9"));
        nodeA.recordFailure("anna", "1.1.1.1");

        assertTrue(nodeB.retryAfter("anna", "9.9.9.9").compareTo(Duration.ZERO) > 0, "node B sees node A's failures");

        nodeB.recordSuccess("anna");
        assertEquals(Duration.ZERO, nodeA.retryAfter("anna", "9.9.9.9"));
    }

    @Test
    void failuresExpireWithTheWindow() {
        var store = new RedisFailureStore(redis, prefix);
        store.add("k", t0, Duration.ofMinutes(1));
        assertEquals(1, store.window("k", t0.plusSeconds(30), Duration.ofMinutes(1)).count());
        assertEquals(0, store.window("k", t0.plusSeconds(61), Duration.ofMinutes(1)).count());
    }

    @Test
    void fallsBackToLocalCountersWhenRedisIsDown() {
        var dead = new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", 1));
        dead.afterPropertiesSet();
        var broken = new StringRedisTemplate(dead);
        broken.afterPropertiesSet();
        FailureStore store = new RedisFailureStore(broken, prefix);

        store.add("k", t0, Duration.ofMinutes(1));
        store.add("k", t0, Duration.ofMinutes(1));

        assertEquals(2, store.window("k", t0, Duration.ofMinutes(1)).count());
        dead.destroy();
    }

    @Test
    void inMemoryStoreEvictsOldFailures() {
        var store = new InMemoryFailureStore();
        store.add("k", t0, Duration.ofMinutes(1));
        assertEquals(0, store.window("k", t0.plusSeconds(61), Duration.ofMinutes(1)).count());
    }
}
