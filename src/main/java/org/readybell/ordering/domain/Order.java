package org.readybell.ordering.domain;

import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.DomainEvent;
import org.readybell.shared.domain.TenantId;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate root representing a customer order and its lifecycle.
 *
 * <p>Design invariants:
 * <ul>
 *   <li>An order is always created in the {@link OrderStatus#CREATED} state (Req 4.1).</li>
 *   <li>The creating staff member's {@link TenantId} is assigned at creation and never
 *       changes (Req 4.2, 10.1).</li>
 *   <li>An order is always associated with a {@link CustomerSessionId} (Req 4.4).</li>
 *   <li>Transitions follow the allowed lifecycle graph only (Req 5.1, 5.2):
 *       CREATED → IN_PROGRESS → READY → COMPLETED, with CANCELLED reachable from
 *       CREATED, IN_PROGRESS, or READY.</li>
 *   <li>Self-transitions are rejected (Req 5.4).</li>
 *   <li>Transitions out of a terminal state ({@link OrderStatus#COMPLETED} or
 *       {@link OrderStatus#CANCELLED}) are rejected (Req 5.7).</li>
 *   <li>Reaching {@link OrderStatus#READY} records both {@link OrderStatusUpdated}
 *       and {@link OrderReady} (Req 5.5, 5.6).</li>
 *   <li>{@link #version} is an optimistic-lock guard; the persistence adapter maps it to
 *       a JPA {@code @Version} column (Req 5.8).</li>
 * </ul>
 *
 * <p>There is no public constructor; {@link #create(TenantId, CustomerSessionId, Clock)}
 * is the sole creation path. State-changing methods record {@link DomainEvent}s on
 * {@link #pendingEvents}; the application layer drains and publishes them after
 * the transaction commits.
 */
public class Order {

    private final OrderId id;
    private final TenantId tenantId;
    private final CustomerSessionId sessionId;
    private OrderStatus status;
    private final String customerName;
    private final List<OrderItem> items;
    private final Instant estimatedReadyAt;
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * Optimistic-lock version counter.
     *
     * <p>Starts at {@code 0} and is incremented by the persistence adapter on every
     * successful write. Guards against concurrent state transitions so at most one of
     * two conflicting transitions succeeds (Req 5.8).
     */
    private long version;

    /** Pending events to be published by the application layer after transaction commit. */
    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    private Order(OrderId id,
                  TenantId tenantId,
                  CustomerSessionId sessionId,
                  OrderStatus status,
                  OrderDetails details,
                  Instant createdAt,
                  Instant updatedAt) {
        Objects.requireNonNull(id,        "id must not be null");
        Objects.requireNonNull(tenantId,  "tenantId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(status,    "status must not be null");
        this.id        = id;
        this.tenantId  = tenantId;
        this.sessionId = sessionId;
        this.status    = status;
        this.customerName     = details.customerName();
        this.items            = details.items();
        this.estimatedReadyAt = details.estimatedReadyAt();
        this.createdAt        = createdAt;
        this.updatedAt        = updatedAt;
        this.version   = 0L;
    }

    /**
     * Creates a new {@code Order} in the {@link OrderStatus#CREATED} state.
     *
     * <p>Assigns a fresh {@link OrderId}, stamps the provided {@code tenantId} and
     * {@code sessionId}, and records an {@link OrderCreated} event for later
     * publication (Req 4.1, 4.2, 4.3, 4.4).
     *
     * @param tenantId  the restaurant creating the order (Req 4.2)
     * @param sessionId the customer session the order belongs to (Req 4.4)
     * @param clock     clock used to timestamp the event
     * @return a new CREATED order with one pending {@link OrderCreated} event
     */
    public static Order create(TenantId tenantId, CustomerSessionId sessionId, Clock clock) {
        return create(tenantId, sessionId, clock, OrderDetails.NONE);
    }

    /** As {@link #create(TenantId, CustomerSessionId, Clock)}, with customer name, items and ETA. */
    public static Order create(TenantId tenantId, CustomerSessionId sessionId, Clock clock, OrderDetails details) {
        Objects.requireNonNull(tenantId,  "tenantId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(clock,     "clock must not be null");
        Objects.requireNonNull(details,   "details must not be null");

        Instant now = clock.instant();
        var order = new Order(OrderId.newId(), tenantId, sessionId, OrderStatus.CREATED, details, now, now);
        order.record(OrderCreated.of(order.id, tenantId, sessionId, clock.instant()));
        return order;
    }

    /**
     * Transitions this order to the given {@code target} status, enforcing all
     * lifecycle invariants.
     *
     * <p>Invariant checks (in evaluation order):
     * <ol>
     *   <li>Self-transition → {@link InvalidTransitionException} (Req 5.4)</li>
     *   <li>Already terminal → {@link TerminalStateException} (Req 5.7)</li>
     *   <li>Disallowed pair → {@link InvalidTransitionException} (Req 5.3)</li>
     * </ol>
     *
     * <p>On success, records an {@link OrderStatusUpdated} event (Req 5.5). If
     * {@code target} is {@link OrderStatus#READY}, additionally records an
     * {@link OrderReady} event (Req 5.6).
     *
     * @param target the desired new status
     * @param clock  clock used to timestamp the recorded events
     * @throws InvalidTransitionException if {@code target} equals the current status,
     *                                    or if the transition is not in the allowed set
     * @throws TerminalStateException     if the order is already in a terminal state
     */
    public void transitionTo(OrderStatus target, Clock clock) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(clock,  "clock must not be null");

        if (status == target) {
            throw new InvalidTransitionException(status, target);      // Req 5.4
        }
        if (status.isTerminal()) {
            throw new TerminalStateException(status);                  // Req 5.7
        }
        if (!status.canTransitionTo(target)) {
            throw new InvalidTransitionException(status, target);      // Req 5.3
        }

        var from = status;
        status = target;
        updatedAt = clock.instant();

        record(OrderStatusUpdated.of(id, tenantId, sessionId, from, target, clock.instant())); // Req 5.5
        if (target == OrderStatus.READY) {
            record(OrderReady.of(id, tenantId, sessionId, clock.instant()));                   // Req 5.6
        }
    }

    public OrderId getId() {
        return id;
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public CustomerSessionId getSessionId() {
        return sessionId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    /** Customer display name, or {@code null} when not provided. */
    public String getCustomerName() {
        return customerName;
    }

    /** Immutable list of ordered lines (possibly empty). */
    public List<OrderItem> getItems() {
        return items;
    }

    /** Expected ready time, or {@code null} when not provided. */
    public Instant getEstimatedReadyAt() {
        return estimatedReadyAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Returns the current optimistic-lock version.
     *
     * <p>The persistence adapter increments this value on every successful write and
     * rejects writes where the stored version does not match, ensuring at most one
     * concurrent transition succeeds (Req 5.8).
     */
    public long getVersion() {
        return version;
    }

    /**
     * Returns an unmodifiable snapshot of the pending domain events.
     * The application layer should call {@link #clearPendingEvents()} after publishing.
     */
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

    /**
     * Rebuilds an {@code Order} from its stored state.
     *
     * <p>Unlike the {@link #create} factory this method does <em>not</em> generate a new id
     * or record any domain event. Intended for use by the JPA persistence adapter only.
     *
     * @param id        the persisted order identity
     * @param tenantId  the persisted tenant
     * @param sessionId the persisted customer session
     * @param status    the persisted order status
     * @param version   the persisted optimistic-lock version
     * @return an order aggregate with no pending events
     */
    public static Order reconstitute(OrderId id,
                                     TenantId tenantId,
                                     CustomerSessionId sessionId,
                                     OrderStatus status,
                                     long version) {
        return reconstitute(id, tenantId, sessionId, status, version, OrderDetails.NONE,
                Instant.EPOCH, Instant.EPOCH);
    }

    /** Full reconstitution including descriptive data and timestamps (persistence adapter only). */
    public static Order reconstitute(OrderId id,
                                     TenantId tenantId,
                                     CustomerSessionId sessionId,
                                     OrderStatus status,
                                     long version,
                                     OrderDetails details,
                                     Instant createdAt,
                                     Instant updatedAt) {
        var order = new Order(id, tenantId, sessionId, status, details, createdAt, updatedAt);
        order.version = version;
        return order;
    }
}
