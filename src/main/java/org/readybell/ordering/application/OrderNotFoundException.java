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
 *       records are silently excluded from tenant-scoped lookups (Req 10.3).</li>
 * </ul>
 *
 * <p>The order remains unchanged when this exception is thrown (Req 5.3).
 */
public class OrderNotFoundException extends RuntimeException {

    private final TenantId tenantId;
    private final OrderId orderId;

    /**
     * @param tenantId the tenant scope in which the lookup was performed
     * @param orderId  the order identity that was not found
     */
    public OrderNotFoundException(TenantId tenantId, OrderId orderId) {
        super("Order not found for tenant " + tenantId.value()
                + " and orderId " + orderId.value());
        this.tenantId = tenantId;
        this.orderId  = orderId;
    }

    /** The tenant scope used during the lookup. */
    public TenantId getTenantId() {
        return tenantId;
    }

    /** The order identity that was not found. */
    public OrderId getOrderId() {
        return orderId;
    }
}
