package org.readybell.restaurant.application;

import org.readybell.restaurant.domain.StaffAccountId;
import org.readybell.shared.domain.TenantId;

/**
 * Thrown when a staff account cannot be found within a given tenant (Req 8.8).
 *
 * <p>This covers both the case where the account does not exist and the case where it
 * belongs to a different tenant — both surfaces as "not found" to avoid leaking
 * cross-tenant information.
 */
public class StaffAccountNotFoundException extends RuntimeException {

    private final TenantId tenantId;
    private final StaffAccountId accountId;

    public StaffAccountNotFoundException(TenantId tenantId, StaffAccountId accountId) {
        super("Staff account " + accountId.value() + " not found for tenant " + tenantId.value());
        this.tenantId = tenantId;
        this.accountId = accountId;
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public StaffAccountId getAccountId() {
        return accountId;
    }
}
