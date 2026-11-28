package org.readybell.ordering.application;

import org.readybell.ordering.domain.InvalidTransitionException;
import org.readybell.ordering.domain.Order;
import org.readybell.ordering.domain.OrderDetails;
import org.readybell.shared.domain.TenantId;
import org.readybell.ordering.domain.TerminalStateException;

/**
 * Inbound port (use-case interface) for managing {@link Order} lifecycle.
 *
 * <p>This interface is the boundary between inbound adapters (REST controllers, tests)
 * and the application service that orchestrates the domain. Wiring happens in the
 * composition root.
 */
public interface ManageOrderUseCase {
    /**
     * Creates a new order for the session identified in the command.
     *
     * <p>The service validates that the target session exists, is active, and belongs
     * to the same tenant as the staff member before creating the order.
     *
     * @throws InvalidSessionException if the session is not found or belongs to another
     *         tenant
     */
    Order createOrder(CreateOrderCommand command);

    /**
     * Staff-side order entry: starts a fresh customer session for the tenant and creates the order in it.
     * The session id is what the receipt QR code points to, so the customer can track this order
     * without any prior scan.
     */
    Order createOrderWithNewSession(TenantId tenantId, OrderDetails details);

    /**
     * Transitions an existing order to a new status.
     *
     * <p>Steps performed:
     * <ol>
     *   <li>Load the order scoped to the command's {@code tenantId}; if absent throw {@link OrderNotFoundException}.</li>
     *   <li>Apply {@link Order#transitionTo} — domain
     *       invariants are enforced there.</li>
     *   <li>Persist under optimistic locking so at most one concurrent transition
     *       wins.</li>
     *   <li>Publish all pending events ({@code OrderStatusUpdated} always;
     *       {@code OrderReady} additionally when target is READY).</li>
     *   <li>Return the updated {@link Order}.</li>
     * </ol>
     *
     * @throws OrderNotFoundException      if no order is found for the given tenant
     *                                     and order id
     * @throws InvalidTransitionException  if the transition is disallowed or a
     *                                     self-transition
     * @throws TerminalStateException      if the order is already in a terminal state
     */
    Order transitionOrderStatus(TransitionOrderCommand command);
}
