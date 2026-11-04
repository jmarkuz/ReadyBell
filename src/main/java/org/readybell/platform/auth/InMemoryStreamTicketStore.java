package org.readybell.platform.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Per-node ticket store (the default; also the fallback when Redis is unreachable). */
public class InMemoryStreamTicketStore implements StreamTicketStore {

    private static final int MAX_TICKETS = 10_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private record Entry(String jwt, Instant expiresAt) {}

    private final Map<String, Entry> tickets = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryStreamTicketStore(Clock clock) {
        this.clock = clock;
    }

    static String newTicket() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public String issue(String jwt, Duration ttl) {
        Instant now = clock.instant();
        if (tickets.size() >= MAX_TICKETS) {
            tickets.values().removeIf(e -> !e.expiresAt().isAfter(now));
        }
        String ticket = newTicket();
        tickets.put(ticket, new Entry(jwt, now.plus(ttl)));
        return ticket;
    }

    @Override
    public Optional<String> consume(String ticket) {
        Entry entry = ticket == null ? null : tickets.remove(ticket);
        if (entry == null || !entry.expiresAt().isAfter(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(entry.jwt());
    }
}
