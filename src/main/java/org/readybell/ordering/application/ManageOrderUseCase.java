package org.readybell.ordering.application;

import org.readybell.ordering.domain.Order;
import org.readybell.ordering.domain.OrderDetails;
import org.readybell.shared.domain.TenantId;

/**
 * Inbound port (use-case interface) for managing {@link Order} lifecycle.
 *
 * <p>This interface is the boundary between inbound adapters (REST controllers, tests)
 * and the application service that orchestrates the domain. Wiring happens in the
 * composition root.
 */
public interface ManageOrderUseCase {
    /** Creates a new order for the session identified in the command. */
    Order createOrder(CreateOrderCommand command);

    /** Staff-side order entry: starts a fresh customer session for the tenant and creates the order in it. */
    Order createOrderWithNewSession(TenantId tenantId, OrderDetails details);

    /** Transitions an existing order to a new status. */
    Order transitionOrderStatus(TransitionOrderCommand command);
}
