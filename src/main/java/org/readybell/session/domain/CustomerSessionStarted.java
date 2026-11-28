package org.readybell.session.domain;

import org.readybell.shared.domain.DomainEvent;
import org.readybell.shared.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain event emitted when a new {@link CustomerSession} is started.
 *
 * <p>Published through the {@code DomainEventPublisher} port after a successful
 * QR-code scan creates an ACTIVE session.
 */
public record CustomerSessionStarted(
        UUID eventId,
        CustomerSessionId sessionId,
        TenantId tenantId,
        Instant occurredAt
) implements DomainEvent {
    public CustomerSessionStarted {
        Objects.requireNonNull(eventId,    "eventId must not be null");
        Objects.requireNonNull(sessionId,  "sessionId must not be null");
        Objects.requireNonNull(tenantId,   "tenantId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    /** Convenience factory that generates a fresh {@code eventId} automatically. */
    public static CustomerSessionStarted of(CustomerSessionId sessionId,
                                             TenantId tenantId,
                                             Instant occurredAt) {
        return new CustomerSessionStarted(UUID.randomUUID(), sessionId, tenantId, occurredAt);
    }
}
