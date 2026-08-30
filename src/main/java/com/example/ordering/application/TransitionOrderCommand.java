package com.example.ordering.application;

import com.example.ordering.domain.OrderId;
import com.example.ordering.domain.OrderStatus;
import com.example.shared.domain.TenantId;

import java.util.Objects;

/**
 * Command object carrying the inputs for an order-status transition.
 *
 * <p>Passed to {@link ManageOrderUseCase#transitionOrderStatus(TransitionOrderCommand)}
 * by an inbound adapter (REST controller, test). Immutable; validated at construction
 * time so the application service can assume all fields are non-null.
 *
 * @param tenantId     the tenant that owns the order — used for tenant-scoped lookup
 *                     (Req 10.2, 10.4)
 * @param orderId      identity of the order to transition
 * @param targetStatus the desired new {@link OrderStatus}
 */
public record TransitionOrderCommand(
        TenantId tenantId,
        OrderId orderId,
        OrderStatus targetStatus
) {
    public TransitionOrderCommand {
        Objects.requireNonNull(tenantId,     "tenantId must not be null");
        Objects.requireNonNull(orderId,      "orderId must not be null");
        Objects.requireNonNull(targetStatus, "targetStatus must not be null");
    }
}
