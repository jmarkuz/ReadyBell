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
 * A customer order and its lifecycle: CREATED, IN_PROGRESS, READY, COMPLETED, with CANCELLED reachable from any open state.
 * Self-transitions and transitions out of a terminal state are rejected. {@link #create} is the only way to make a new one.
 *
 * <p>State changes record {@link DomainEvent}s in {@link #pendingEvents}; the application layer publishes them. Reaching
 * READY records both {@link OrderStatusUpdated} and {@link OrderReady}.
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

    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    /** True from {@link #create} until the persistence adapter has stored the order, so it is inserted rather than merged. */
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

    public static Order create(TenantId tenantId, CustomerSessionId sessionId, Clock clock) {
        return create(tenantId, sessionId, clock, OrderDetails.NONE, 1);
    }

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
     * @throws InvalidTransitionException if {@code target} is the current status or not an allowed next status
     * @throws TerminalStateException     if the order is already completed or cancelled
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

    public void markPersisted() {
        newlyCreated = false;
    }

    public void clearPendingEvents() {
        pendingEvents.clear();
    }

    /** Rebuilds an order from its stored state, without a new id or events (persistence adapter only). */
    public static Order reconstitute(OrderId id,
                                     TenantId tenantId,
                                     CustomerSessionId sessionId,
                                     OrderStatus status,
                                     long version) {
        return reconstitute(id, 0, tenantId, sessionId, status, version, OrderDetails.NONE,
                Instant.EPOCH, Instant.EPOCH);
    }

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
