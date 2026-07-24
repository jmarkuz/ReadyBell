package org.readybell.restaurant.adapter.in.web;

import org.readybell.restaurant.application.RestaurantAdminUseCase;
import org.readybell.restaurant.domain.Restaurant;
import org.readybell.restaurant.domain.RestaurantConfig;
import org.readybell.restaurant.domain.Role;
import org.readybell.restaurant.domain.StaffAccount;
import org.readybell.restaurant.domain.StaffAccountId;
import org.readybell.shared.domain.TenantId;
import org.readybell.shared.tenancy.TenantContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Inbound REST adapter for the Restaurant Admin API (Req 7.1, 7.2, 8.1, 8.2, 8.3, 8.6, 8.8).
 *
 * <p>All operations are tenant-scoped: the tenant is resolved from the current
 * authentication context via {@link TenantContextHolder#require()}, granting access
 * exclusively to records matching that tenant (Req 8.1, 8.8).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST   /api/admin/staff}           — create a staff account (Req 8.3)</li>
 *   <li>{@code DELETE /api/admin/staff/{accountId}} — deactivate a staff account (Req 8.5)</li>
 *   <li>{@code PUT    /api/admin/config}           — update restaurant config (Req 8.6)</li>
 * </ul>
 *
 * <p>Authentication guards (Req 7.1, 7.2, 8.1, 8.2) are enforced by Spring Security in the
 * composition root (task 21); this controller assumes the tenant context has already been
 * populated by the tenant-resolution filter.
 */
@RestController
@RequestMapping("/api/admin")
public class RestaurantAdminController {

    private final RestaurantAdminUseCase adminUseCase;

    public RestaurantAdminController(RestaurantAdminUseCase adminUseCase) {
        this.adminUseCase = adminUseCase;
    }

    // -------------------------------------------------------------------------
    // POST /api/admin/staff — create staff account (Req 8.3, 8.4)
    // -------------------------------------------------------------------------

    /**
     * Creates a new staff account within the authenticated admin's restaurant.
     *
     * @param request body containing {@code username} and {@code role}
     * @return 201 Created with the new account details
     */
    @PostMapping("/staff")
    @ResponseStatus(HttpStatus.CREATED)
    public StaffAccountResponse createStaffAccount(@RequestBody CreateStaffRequest request) {
        TenantId tenantId = TenantContextHolder.require();
        Role role = Role.valueOf(request.role());
        StaffAccount account = adminUseCase.createStaffAccount(tenantId, request.username(), role);
        return StaffAccountResponse.from(account);
    }

    // -------------------------------------------------------------------------
    // DELETE /api/admin/staff/{accountId} — deactivate staff account (Req 8.5, 8.8)
    // -------------------------------------------------------------------------

    /**
     * Deactivates the staff account identified by {@code accountId}.
     *
     * <p>Cross-tenant access is denied by the use case (Req 8.8); the account
     * must belong to the authenticated admin's tenant.
     *
     * @param accountId the UUID of the staff account to deactivate
     * @return 200 OK with the deactivated account details
     */
    @DeleteMapping("/staff/{accountId}")
    public StaffAccountResponse deactivateStaffAccount(@PathVariable("accountId") UUID accountId) {
        TenantId tenantId = TenantContextHolder.require();
        StaffAccountId staffAccountId = new StaffAccountId(accountId);
        StaffAccount account = adminUseCase.deactivateStaffAccount(tenantId, staffAccountId);
        return StaffAccountResponse.from(account);
    }

    // -------------------------------------------------------------------------
    // PUT /api/admin/config — update restaurant configuration (Req 8.6, 8.7)
    // -------------------------------------------------------------------------

    /**
     * Updates the restaurant configuration for the authenticated admin's tenant.
     *
     * @param request body containing the new {@code name}
     * @return 200 OK with the updated configuration
     */
    @PutMapping("/config")
    public RestaurantConfigResponse updateConfig(@RequestBody UpdateConfigRequest request) {
        TenantId tenantId = TenantContextHolder.require();
        RestaurantConfig config = RestaurantConfig.of(request.name());
        Restaurant restaurant = adminUseCase.updateRestaurantConfig(tenantId, config);
        return new RestaurantConfigResponse(restaurant.getConfig().name());
    }

    // -------------------------------------------------------------------------
    // Request / Response records
    // -------------------------------------------------------------------------

    /**
     * Request body for {@code POST /api/admin/staff}.
     *
     * @param username the desired username, unique within the tenant (Req 8.4)
     * @param role     the role string, must be {@code STAFF} or {@code RESTAURANT_ADMIN}
     */
    public record CreateStaffRequest(String username, String role) {}

    /**
     * Response body for staff account operations.
     *
     * @param accountId the staff account identifier
     * @param username  the account's username
     * @param role      the account's role
     */
    public record StaffAccountResponse(String accountId, String username, String role) {

        static StaffAccountResponse from(StaffAccount account) {
            return new StaffAccountResponse(
                    account.getId().value().toString(),
                    account.getUsername(),
                    account.getRole().name()
            );
        }
    }

    /**
     * Request body for {@code PUT /api/admin/config}.
     *
     * @param name the new display name for the restaurant
     */
    public record UpdateConfigRequest(String name) {}

    /**
     * Response body for {@code PUT /api/admin/config}.
     *
     * @param name the persisted display name of the restaurant
     */
    public record RestaurantConfigResponse(String name) {}
}
