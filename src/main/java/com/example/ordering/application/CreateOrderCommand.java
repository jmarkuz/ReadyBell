package com.example.ordering.application;

import com.example.session.domain.CustomerSessionId;
import com.example.shared.domain.TenantId;

import java.util.Objects;

/**
 * Command object carrying the inputs for order creation.
 *
 * <p>Passed to {@link ManageOrderUseCase#createOrder(CreateOrderCommand)} by an
 * inbound adapter. Immutable; validated at construction time.
 *
 * @param tenantId  the staff member's tenant — the order will be stamped with this
 *                  {@link TenantId} (Req 4.2, 10.1)
 * @param sessionId the customer session this order belongs to (Req 4.4)
 */
public record CreateOrderCommand(
        TenantId tenantId,
        CustomerSessionId sessionId
) {
    public CreateOrderCommand {
        Objects.requireNonNull(tenantId,   "tenantId must not be null");
        Objects.requireNonNull(sessionId,  "sessionId must not be null");
    }
}
