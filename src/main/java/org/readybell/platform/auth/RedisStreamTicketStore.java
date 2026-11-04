package org.readybell.platform.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * Tickets in Redis ({@code SET ... EX} to issue, {@code GETDEL} to consume) so a ticket issued by one
 * replica opens the stream on another. If Redis is unreachable it degrades to per-node tickets.
 */
public class RedisStreamTicketStore implements StreamTicketStore {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamTicketStore.class);

    private final StringRedisTemplate redis;
    private final String prefix;
    private final InMemoryStreamTicketStore fallback;

    public RedisStreamTicketStore(StringRedisTemplate redis, String prefix, Clock clock) {
        this.redis = redis;
        this.prefix = prefix;
        this.fallback = new InMemoryStreamTicketStore(clock);
    }

    @Override
    public String issue(String jwt, Duration ttl) {
        try {
            String ticket = InMemoryStreamTicketStore.newTicket();
            redis.opsForValue().set(prefix + ticket, jwt, ttl);
            return ticket;
        } catch (RuntimeException e) {
            log.warn("Redis ticket store unavailable, using a per-node ticket: {}", e.getMessage());
            return fallback.issue(jwt, ttl);
        }
    }

    @Override
    public Optional<String> consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return Optional.empty();
        }
        Optional<String> local = fallback.consume(ticket);
        if (local.isPresent()) {
            return local;
        }
        try {
            return Optional.ofNullable(redis.opsForValue().getAndDelete(prefix + ticket));
        } catch (RuntimeException e) {
            log.warn("Redis ticket store unavailable: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
