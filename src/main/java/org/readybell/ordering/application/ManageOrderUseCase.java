package org.readybell.ordering.application;

import org.readybell.ordering.domain.InvalidTransitionException;
import org.readybell.ordering.domain.Order;
import org.readybell.ordering.domain.TerminalStateException;

/**
 * Inbound port (use-case interface) for managing {@link Order} lifecycle.
 *
 * <p>This interface is the boundary between inbound adapters (REST controllers, tests)
 * and the application service that orchestrates the domain. Wiring happens in the
 * composition root.
 *
 * <p>Requirements covered: 4.1–4.6, 5.1–5.8.
 */
public interface ManageOrderUseCase {

    /**
     * Creates a new order for the session identified in the command.
     *
     * <p>The service validates that the target session exists, is active, and belongs
     * to the same tenant as the staff member before creating the order (Req 4.4–4.6).
     *
     * @param command the creation command carrying the tenant and session identity
     * @return the newly created, CREATED-state {@link Order}
     * @throws org.readybell.session.application.InvalidQrCodeException or an equivalent
     *         application exception if the session is not found or belongs to another
     *         tenant (Req 4.5, 4.6)
     */
    Order createOrder(CreateOrderCommand command);

    /**
     * Transitions an existing order to a new status.
     *
     * <p>Steps performed:
     * <ol>
     *   <li>Load the order scoped to the command's {@code tenantId}
     *       (Req 10.2, 10.4); if absent throw {@link OrderNotFoundException}.</li>
     *   <li>Apply {@link org.readybell.ordering.domain.Order#transitionTo} — domain
     *       invariants are enforced there (Req 5.1–5.7).</li>
     *   <li>Persist under optimistic locking so at most one concurrent transition
     *       wins (Req 5.8).</li>
     *   <li>Publish all pending events ({@code OrderStatusUpdated} always;
     *       {@code OrderReady} additionally when target is READY) (Req 5.5, 5.6).</li>
     *   <li>Return the updated {@link Order}.</li>
     * </ol>
     *
     * @param command the transition command carrying tenant, order id, and target status
     * @return the {@link Order} after the successful transition
     * @throws OrderNotFoundException      if no order is found for the given tenant
     *                                     and order id (Req 10.3)
     * @throws InvalidTransitionException  if the transition is disallowed or a
     *                                     self-transition (Req 5.3, 5.4)
     * @throws TerminalStateException      if the order is already in a terminal state
     *                                     (Req 5.7)
     */
    Order transitionOrderStatus(TransitionOrderCommand command);
}
