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
 *   <li>A session always has a non-null {@link TenantId} assigned at creation.</li>
 *   <li>Creation requires no customer credentials.</li>
 *   <li>Each QR scan produces a distinct {@link CustomerSessionId}.</li>
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

    // Factory — only way to create a session

    /**
     * Creates a new, ACTIVE {@code CustomerSession} for the given tenant.
     *
     * <p>Automatically assigns a fresh {@link CustomerSessionId} so every scan
     * produces a distinct session. Records a {@link CustomerSessionStarted}
     * event for later publication.
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

    public boolean isActive() {
        return status == SessionStatus.ACTIVE;
    }

    public List<DomainEvent> getPendingEvents() {
        return Collections.unmodifiableList(pendingEvents);
    }

    private void record(DomainEvent event) {
        pendingEvents.add(event);
    }

    /** Clears the pending events list after they have been published. */
    public void clearPendingEvents() {
        pendingEvents.clear();
    }

    // Reconstitution — used by the JPA adapter to rebuild the aggregate from a
    // stored entity without recording any domain events (no side-effects).

    /**
     * Rebuilds a {@code CustomerSession} from its stored state.
     *
     * <p>Unlike the {@link #start} factory this method does <em>not</em> generate a new id
     * or record any domain event — it simply restores the aggregate to the state that was
     * persisted. Intended for use by the JPA persistence adapter only.
     */
    public static CustomerSession reconstitute(CustomerSessionId id,
                                               TenantId tenantId,
                                               SessionStatus status,
                                               Instant startedAt) {
        return new CustomerSession(id, tenantId, status, startedAt);
    }
}
