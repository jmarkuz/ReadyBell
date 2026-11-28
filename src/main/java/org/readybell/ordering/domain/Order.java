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
 *   <li>An order is always created in the {@link OrderStatus#CREATED} state.</li>
 *   <li>The creating staff member's {@link TenantId} is assigned at creation and never
 *       changes.</li>
 *   <li>An order is always associated with a {@link CustomerSessionId}.</li>
 *   <li>Transitions follow the allowed lifecycle graph only:
 *       CREATED → IN_PROGRESS → READY → COMPLETED, with CANCELLED reachable from
 *       CREATED, IN_PROGRESS, or READY.</li>
 *   <li>Self-transitions are rejected.</li>
 *   <li>Transitions out of a terminal state ({@link OrderStatus#COMPLETED} or
 *       {@link OrderStatus#CANCELLED}) are rejected.</li>
 *   <li>Reaching {@link OrderStatus#READY} records both {@link OrderStatusUpdated}
 *       and {@link OrderReady}.</li>
 *   <li>{@link #version} is an optimistic-lock guard; the persistence adapter maps it to
 *       a JPA {@code @Version} column.</li>
 * </ul>
 *
 * <p>There is no public constructor; {@link #create(TenantId, CustomerSessionId, Clock)}
 * is the sole creation path. State-changing methods record {@link DomainEvent}s on
 * {@link #pendingEvents}; the application layer drains and publishes them after
 * the transaction commits.
 */
public class Order {
    private final OrderId id;
    /** Human-friendly per-restaurant sequence number (1, 2, 3 ...), unique within the tenant. */
    private final int orderNumber;
    private final TenantId tenantId;
    private final CustomerSessionId sessionId;
    private OrderStatus status;
    private final String customerName;
    private final List<OrderItem> items;
    private final Instant estimatedReadyAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant connectedAt;

    private long version;

    /** Pending events to be published by the application layer after transaction commit. */
    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    private boolean newlyCreated;

    private Order(OrderId id,
                  int orderNumber,
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
        if (orderNumber < 0) {
            throw new IllegalArgumentException("orderNumber must not be negative");
        }
        this.id        = id;
        this.orderNumber = orderNumber;
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
     * publication.
     */
    public static Order create(TenantId tenantId, CustomerSessionId sessionId, Clock clock) {
        return create(tenantId, sessionId, clock, OrderDetails.NONE, 1);
    }

    /**
     * As {@link #create(TenantId, CustomerSessionId, Clock)}, with customer name, items and ETA, and the
     * restaurant's next sequential order number (allocated by an {@code OrderNumberGenerator}).
     */
    public static Order create(TenantId tenantId, CustomerSessionId sessionId, Clock clock, OrderDetails details,
                               int orderNumber) {
        Objects.requireNonNull(tenantId,  "tenantId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(clock,     "clock must not be null");
        Objects.requireNonNull(details,   "details must not be null");

        Instant now = clock.instant();
        var order = new Order(OrderId.newId(), orderNumber, tenantId, sessionId, OrderStatus.CREATED, details, now, now);
        order.record(OrderCreated.of(order.id, tenantId, sessionId, clock.instant(), orderNumber));
        order.newlyCreated = true;
        return order;
    }

    /**
     * Transitions this order to the given {@code target} status, enforcing all
     * lifecycle invariants.
     *
     * <p>Invariant checks (in evaluation order):
     * <ol>
     *   <li>Self-transition → {@link InvalidTransitionException}</li>
     *   <li>Already terminal → {@link TerminalStateException}</li>
     *   <li>Disallowed pair → {@link InvalidTransitionException}</li>
     * </ol>
     *
     * <p>On success, records an {@link OrderStatusUpdated} event. If
     * {@code target} is {@link OrderStatus#READY}, additionally records an
     * {@link OrderReady} event.
     *
     * @throws InvalidTransitionException if {@code target} equals the current status,
     *                                    or if the transition is not in the allowed set
     * @throws TerminalStateException     if the order is already in a terminal state
     */
    public void transitionTo(OrderStatus target, Clock clock) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(clock,  "clock must not be null");

        if (status == target) {
            throw new InvalidTransitionException(status, target);
        }
        if (status.isTerminal()) {
            throw new TerminalStateException(status);
        }
        if (!status.canTransitionTo(target)) {
            throw new InvalidTransitionException(status, target);
        }

        var from = status;
        status = target;
        updatedAt = clock.instant();

        record(OrderStatusUpdated.of(id, tenantId, sessionId, from, target, clock.instant()));
        if (target == OrderStatus.READY) {
            record(OrderReady.of(id, tenantId, sessionId, clock.instant()));
        }
    }

    public OrderId getId() {
        return id;
    }

    public int getOrderNumber() {
        return orderNumber;
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

    public String getCustomerName() {
        return customerName;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public Instant getEstimatedReadyAt() {
        return estimatedReadyAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public List<DomainEvent> getPendingEvents() {
        return Collections.unmodifiableList(pendingEvents);
    }

    private void record(DomainEvent event) {
        pendingEvents.add(event);
    }

    /**
     * Records that the customer opened the tracking link, so the staff can see that the phone is connected and hide the
     * QR code. Only the first open counts.
     */
    public boolean markConnected(Clock clock) {
        if (connectedAt != null) {
            return false;
        }
        connectedAt = clock.instant();
        return true;
    }

    public boolean isConnected() {
        return connectedAt != null;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public boolean isNew() {
        return newlyCreated;
    }

    /** Called by the persistence adapter once the order has been stored; later saves are updates. */
    public void markPersisted() {
        newlyCreated = false;
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
     */
    public static Order reconstitute(OrderId id,
                                     TenantId tenantId,
                                     CustomerSessionId sessionId,
                                     OrderStatus status,
                                     long version) {
        return reconstitute(id, 0, tenantId, sessionId, status, version, OrderDetails.NONE,
                Instant.EPOCH, Instant.EPOCH);
    }

    /** Full reconstitution including descriptive data and timestamps (persistence adapter only). */
    public static Order reconstitute(OrderId id,
                                     int orderNumber,
                                     TenantId tenantId,
                                     CustomerSessionId sessionId,
                                     OrderStatus status,
                                     long version,
                                     OrderDetails details,
                                     Instant createdAt,
                                     Instant updatedAt) {
        return reconstitute(id, orderNumber, tenantId, sessionId, status, version, details, createdAt, updatedAt, null);
    }

    /** Full reconstitution including the first-open marker (persistence adapter only). */
    public static Order reconstitute(OrderId id,
                                     int orderNumber,
                                     TenantId tenantId,
                                     CustomerSessionId sessionId,
                                     OrderStatus status,
                                     long version,
                                     OrderDetails details,
                                     Instant createdAt,
                                     Instant updatedAt,
                                     Instant connectedAt) {
        var order = new Order(id, orderNumber, tenantId, sessionId, status, details, createdAt, updatedAt);
        order.version = version;
        order.connectedAt = connectedAt;
        return order;
    }
}
