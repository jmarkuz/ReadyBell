package org.readybell.restaurant.application;

import org.readybell.restaurant.domain.Restaurant;
import org.readybell.restaurant.domain.RestaurantConfig;
import org.readybell.restaurant.domain.Role;
import org.readybell.restaurant.domain.StaffAccount;
import org.readybell.restaurant.domain.StaffAccountId;
import org.readybell.shared.domain.TenantId;

import java.util.List;

/**
 * Inbound port (use case) for Restaurant Admin operations (Req 8.3–8.8).
 *
 * <p>All operations are scoped to the admin's tenant; cross-tenant operations are
 * rejected (Req 8.8). This interface is plain Java with no framework annotations, in
 * keeping with the domain-purity design goal.
 */
public interface RestaurantAdminUseCase {

    /**
     * Creates a new staff account within the admin's tenant (Req 8.3).
     *
     * <p>The username must be unique within the tenant (Req 8.4); attempting to create
     * a duplicate throws {@link DuplicateUsernameException}.
     *
     * @param tenantId the tenant scope for this operation (must not be null)
     * @param username the desired username; normalised (lower-case) and unique across the platform because sign-in takes only a username
     * @param role     the role assigned to the new account (must not be null)
     * @param passwordHash encoded password (the raw password must never reach this layer)
     * @return the newly created, active {@link StaffAccount}
     * @throws DuplicateUsernameException if {@code username} already exists in the tenant (Req 8.4)
     */
    StaffAccount createStaffAccount(TenantId tenantId, String username, Role role, String passwordHash);

    /**
     * Deactivates a staff account within the admin's tenant (Req 8.5).
     *
     * <p>After deactivation the account's subsequent access to the Restaurant Panel is
     * denied (Req 8.5). The operation is cross-tenant guarded: if the account belongs to
     * a different tenant it is treated as not found (Req 8.8).
     *
     * @param tenantId  the tenant scope for this operation (must not be null)
     * @param accountId the identity of the account to deactivate (must not be null)
     * @return the deactivated {@link StaffAccount}
     * @throws StaffAccountNotFoundException if no account with that id exists in the tenant (Req 8.8)
     */
    StaffAccount deactivateStaffAccount(TenantId tenantId, StaffAccountId accountId);

    /**
     * Updates the configuration of the restaurant identified by the given tenant (Req 8.6).
     *
     * <p>The new configuration replaces the previous one; invalid configuration is
     * rejected by the domain (Req 8.7). Cross-tenant access is denied (Req 8.8).
     *
     * @param tenantId the tenant whose restaurant configuration should be updated (must not be null)
     * @param config   the replacement configuration value object (must not be null)
     * @return the updated {@link Restaurant}
     * @throws RestaurantNotFoundException if no restaurant exists for the given tenant (Req 8.8)
     */
    Restaurant updateRestaurantConfig(TenantId tenantId, RestaurantConfig config);

    /** The tenant's own restaurant (name and identifier for the dashboard). */
    Restaurant getRestaurant(TenantId tenantId);

    /** All staff accounts of the tenant. */
    List<StaffAccount> listStaffAccounts(TenantId tenantId);
}
