package org.readybell.session.domain;

import org.readybell.shared.domain.DomainEvent;
import org.readybell.shared.domain.TenantId;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate root representing one customer's interaction context, created by
 * scanning a QR code.
 *
 * <p>Design invariants:
 * <ul>
 *   <li>A session always has a non-null {@link TenantId} assigned at creation (Req 1.6, 10.1).</li>
 *   <li>Creation requires no customer credentials (Req 1.5).</li>
 *   <li>Each QR scan produces a distinct {@link CustomerSessionId} (Req 1.1).</li>
 *   <li>There is no public constructor; {@link #start(TenantId, Clock)} is the sole
 *       creation path so invariants cannot be bypassed.</li>
 * </ul>
 *
 * <p>State-changing methods record {@link DomainEvent}s on {@link #pendingEvents}.
 * The application layer drains and publishes these events after the transaction commits.
 */
public class CustomerSession {

    private final CustomerSessionId id;
    private final TenantId tenantId;
    private SessionStatus status;
    private final Instant startedAt;

    /** Pending events to be published by the application layer after transaction commit. */
    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Private constructor — use the factory method
    // -------------------------------------------------------------------------

    private CustomerSession(CustomerSessionId id,
                             TenantId tenantId,
                             SessionStatus status,
                             Instant startedAt) {
        Objects.requireNonNull(id,        "id must not be null");
        Objects.requireNonNull(tenantId,  "tenantId must not be null");
        Objects.requireNonNull(status,    "status must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        this.id        = id;
        this.tenantId  = tenantId;
        this.status    = status;
        this.startedAt = startedAt;
    }

    // -------------------------------------------------------------------------
    // Factory — only way to create a session (Req 1.5)
    // -------------------------------------------------------------------------

    /**
     * Creates a new, ACTIVE {@code CustomerSession} for the given tenant.
     *
     * <p>Automatically assigns a fresh {@link CustomerSessionId} so every scan
     * produces a distinct session (Req 1.1). Records a {@link CustomerSessionStarted}
     * event for later publication (Req 1.2).
     *
     * @param tenantId the restaurant whose QR code was scanned (Req 1.6)
     * @param clock    clock used to timestamp the session and its event
     * @return a new ACTIVE session with one pending {@link CustomerSessionStarted} event
     */
    public static CustomerSession start(TenantId tenantId, Clock clock) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(clock,    "clock must not be null");

        var id      = CustomerSessionId.newId();
        var now     = clock.instant();
        var session = new CustomerSession(id, tenantId, SessionStatus.ACTIVE, now);
        session.record(CustomerSessionStarted.of(id, tenantId, now));
        return session;
    }

    // -------------------------------------------------------------------------
    // Query methods
    // -------------------------------------------------------------------------

    public CustomerSessionId getId() {
        return id;
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    /** Returns {@code true} when this session is in the {@link SessionStatus#ACTIVE} state. */
    public boolean isActive() {
        return status == SessionStatus.ACTIVE;
    }

    /**
     * Returns an unmodifiable snapshot of the pending domain events.
     * The application layer should call {@link #clearPendingEvents()} after publishing.
     */
    public List<DomainEvent> getPendingEvents() {
        return Collections.unmodifiableList(pendingEvents);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void record(DomainEvent event) {
        pendingEvents.add(event);
    }

    /** Clears the pending events list after they have been published. */
    public void clearPendingEvents() {
        pendingEvents.clear();
    }
}
