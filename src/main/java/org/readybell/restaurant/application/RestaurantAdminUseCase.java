package org.readybell.restaurant.application;

import org.readybell.restaurant.domain.Restaurant;
import org.readybell.restaurant.domain.RestaurantConfig;
import org.readybell.restaurant.domain.Role;
import org.readybell.restaurant.domain.StaffAccount;
import org.readybell.restaurant.domain.StaffAccountId;
import org.readybell.shared.domain.TenantId;

import java.util.List;

/**
 * Inbound port (use case) for Restaurant Admin operations.
 *
 * <p>All operations are scoped to the admin's tenant; cross-tenant operations are
 * rejected. This interface is plain Java with no framework annotations, in
 * keeping with the domain-purity design goal.
 */
public interface RestaurantAdminUseCase {
    /**
     * Creates a new staff account within the admin's tenant.
     *
     * <p>The username must be unique within the tenant; attempting to create
     * a duplicate throws {@link DuplicateUsernameException}.
     *
     * @throws DuplicateUsernameException if {@code username} already exists in the tenant
     */
    StaffAccount createStaffAccount(TenantId tenantId, String username, Role role, String passwordHash);

    /**
     * Deactivates a staff account within the admin's tenant.
     *
     * <p>After deactivation the account's subsequent access to the Restaurant Panel is
     * denied. The operation is cross-tenant guarded: if the account belongs to
     * a different tenant it is treated as not found.
     *
     * @throws StaffAccountNotFoundException if no account with that id exists in the tenant
     */
    StaffAccount deactivateStaffAccount(TenantId tenantId, StaffAccountId accountId);

    /**
     * Updates the configuration of the restaurant identified by the given tenant.
     *
     * <p>The new configuration replaces the previous one; invalid configuration is
     * rejected by the domain. Cross-tenant access is denied.
     *
     * @throws RestaurantNotFoundException if no restaurant exists for the given tenant
     */
    Restaurant updateRestaurantConfig(TenantId tenantId, RestaurantConfig config);

    Restaurant getRestaurant(TenantId tenantId);

    List<StaffAccount> listStaffAccounts(TenantId tenantId);
}
