package com.example.shared.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Contract implemented by every domain event in the platform.
 *
 * <p>Domain events represent a state change in the domain and are published
 * in-process behind a hexagonal outbound port (Req 11.1, 11.2). The domain
 * model references only this interface and never a concrete messaging
 * implementation.
 */
public interface DomainEvent {

    /** Unique identity of this event occurrence. */
    UUID eventId();

    /** The tenant this event belongs to. */
    TenantId tenantId();

    /** The instant at which the underlying state change occurred. */
    Instant occurredAt();
}
