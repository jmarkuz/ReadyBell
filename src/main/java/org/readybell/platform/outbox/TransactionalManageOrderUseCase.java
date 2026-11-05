package org.readybell.platform.outbox;

import org.readybell.ordering.application.CreateOrderCommand;
import org.readybell.ordering.application.ManageOrderUseCase;
import org.readybell.ordering.application.TransitionOrderCommand;
import org.readybell.ordering.domain.Order;
import org.readybell.ordering.domain.OrderDetails;
import org.readybell.shared.domain.TenantId;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs each order use case in one transaction, so the order row and its outbox events commit (or roll back)
 * together. The service itself stays plain Java.
 */
public class TransactionalManageOrderUseCase implements ManageOrderUseCase {

    private final ManageOrderUseCase delegate;
    private final TransactionTemplate tx;

    public TransactionalManageOrderUseCase(ManageOrderUseCase delegate, TransactionTemplate tx) {
        this.delegate = delegate;
        this.tx = tx;
    }

    @Override
    public Order createOrder(CreateOrderCommand command) {
        return tx.execute(status -> delegate.createOrder(command));
    }

    @Override
    public Order createOrderWithNewSession(TenantId tenantId, OrderDetails details) {
        return tx.execute(status -> delegate.createOrderWithNewSession(tenantId, details));
    }

    @Override
    public Order transitionOrderStatus(TransitionOrderCommand command) {
        return tx.execute(status -> delegate.transitionOrderStatus(command));
    }
}
