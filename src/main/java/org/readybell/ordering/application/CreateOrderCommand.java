package org.readybell.ordering.application;

import org.readybell.ordering.domain.OrderDetails;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.TenantId;

import java.util.Objects;

/**
 * Command object carrying the inputs for order creation.
 *
 * <p>Passed to {@link ManageOrderUseCase#createOrder(CreateOrderCommand)} by an
 * inbound adapter. Immutable; validated at construction time.
 */
public record CreateOrderCommand(
        TenantId tenantId,
        CustomerSessionId sessionId,
        OrderDetails details
) {
    public CreateOrderCommand(TenantId tenantId, CustomerSessionId sessionId) {
        this(tenantId, sessionId, OrderDetails.NONE);
    }

    public CreateOrderCommand {
        Objects.requireNonNull(details,    "details must not be null");
        Objects.requireNonNull(tenantId,   "tenantId must not be null");
        Objects.requireNonNull(sessionId,  "sessionId must not be null");
    }
}
