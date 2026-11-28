package org.readybell.shared.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Contract implemented by every domain event in the platform.
 *
 * <p>Domain events represent a state change in the domain and are published
 * in-process behind a hexagonal outbound port. The domain
 * model references only this interface and never a concrete messaging
 * implementation.
 */
public interface DomainEvent {
    UUID eventId();

    TenantId tenantId();

    /** The instant at which the underlying state change occurred. */
    Instant occurredAt();
}
