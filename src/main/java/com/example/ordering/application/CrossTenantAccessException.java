package com.example.ordering.application;

import com.example.session.domain.CustomerSessionId;
import com.example.shared.domain.TenantId;

/**
 * Thrown when a Restaurant_Staff member requests an Order for a Customer_Session whose
 * {@link TenantId} differs from the tenant of the requesting staff member.
 *
 * <p>Requirement 4.6: if a Restaurant_Staff member requests creation of an Order for a
 * Customer_Session whose Tenant_Id differs from the Tenant_Id of the member's Restaurant,
 * the Order_Service SHALL reject the request without creating an Order and return an error
 * response identifying the Customer_Session as inaccessible.
 *
 * <p>Also aligns with the cross-tenant access policy in Req 10.3.
 */
public class CrossTenantAccessException extends RuntimeException {

    public CrossTenantAccessException(TenantId staffTenantId, CustomerSessionId sessionId) {
        super("Cross-tenant access denied: session " + sessionId.value()
                + " does not belong to tenant " + staffTenantId.value());
    }

    public CrossTenantAccessException(String message) {
        super(message);
    }
}
