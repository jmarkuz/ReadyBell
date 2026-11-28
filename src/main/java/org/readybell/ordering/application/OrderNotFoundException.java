package org.readybell.ordering.application;

import org.readybell.ordering.domain.OrderId;
import org.readybell.shared.domain.TenantId;

/**
 * Thrown by {@link ManageOrderUseCase} when an order cannot be found for the
 * given tenant and order identity.
 *
 * <p>This covers two cases that are deliberately indistinguishable to callers:
 * <ul>
 *   <li>No order with that {@link OrderId} exists at all.</li>
 *   <li>An order with that id exists but belongs to a different tenant — cross-tenant
 *       records are silently excluded from tenant-scoped lookups.</li>
 * </ul>
 *
 * <p>The order remains unchanged when this exception is thrown.
 */
public class OrderNotFoundException extends RuntimeException {
    private final TenantId tenantId;
    private final OrderId orderId;

    public OrderNotFoundException(TenantId tenantId, OrderId orderId) {
        super("Order not found for tenant " + tenantId.value()
                + " and orderId " + orderId.value());
        this.tenantId = tenantId;
        this.orderId  = orderId;
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public OrderId getOrderId() {
        return orderId;
    }
}
