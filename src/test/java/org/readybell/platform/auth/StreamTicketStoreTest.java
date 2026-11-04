package org.readybell.platform.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class StreamTicketStoreTest {

    /** Clock the test can move forward. */
    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void ticketIsSingleUse() {
        var store = new InMemoryStreamTicketStore(new MutableClock());
        String ticket = store.issue("jwt-a", Duration.ofSeconds(30));
        assertThat(store.consume(ticket)).contains("jwt-a");
        assertThat(store.consume(ticket)).isEmpty();
    }

    @Test
    void ticketExpires() {
        var clock = new MutableClock();
        var store = new InMemoryStreamTicketStore(clock);
        String ticket = store.issue("jwt-a", Duration.ofSeconds(30));
        clock.now = clock.now.plusSeconds(31);
        assertThat(store.consume(ticket)).isEmpty();
    }

    @Test
    void ticketsAreUniqueAndBoundToTheirOwnJwt() {
        var store = new InMemoryStreamTicketStore(new MutableClock());
        String a = store.issue("jwt-tenant-a", Duration.ofSeconds(30));
        String b = store.issue("jwt-tenant-b", Duration.ofSeconds(30));
        assertThat(a).isNotEqualTo(b);
        assertThat(store.consume(b)).contains("jwt-tenant-b");
        assertThat(store.consume(a)).contains("jwt-tenant-a");
    }

    @Test
    void unknownOrNullTicketIsEmpty() {
        var store = new InMemoryStreamTicketStore(new MutableClock());
        assertThat(store.consume("nope")).isEmpty();
        assertThat(store.consume(null)).isEmpty();
    }
}
