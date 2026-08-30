package com.example.restaurant.adapter.in.web;

import com.example.platform.GlobalExceptionHandler;
import com.example.restaurant.application.DuplicateUsernameException;
import com.example.restaurant.application.RestaurantNotFoundException;
import com.example.restaurant.application.StaffAccountNotFoundException;
import com.example.restaurant.application.RestaurantAdminUseCase;
import com.example.restaurant.domain.Restaurant;
import com.example.restaurant.domain.RestaurantConfig;
import com.example.restaurant.domain.Role;
import com.example.restaurant.domain.StaffAccount;
import com.example.restaurant.domain.StaffAccountId;
import com.example.shared.domain.TenantId;
import com.example.shared.tenancy.TenantContextHolder;
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
 *   <li>{@code POST   /api/admin/staff}             — create a staff account (Req 8.3)</li>
 *   <li>{@code DELETE /api/admin/staff/{accountId}} — deactivate a staff account (Req 8.5)</li>
 *   <li>{@code PUT    /api/admin/config}             — update restaurant config (Req 8.6)</li>
 * </ul>
 *
 * <p>Authentication guards (Req 7.1, 7.2, 8.1, 8.2) are enforced by Spring Security in the
 * composition root; this controller assumes the tenant context has already been populated by
 * the tenant-resolution filter.
 *
 * <p>Exception mapping ({@link DuplicateUsernameException} →
 * 409, {@link StaffAccountNotFoundException} → 404,
 * {@link RestaurantNotFoundException} → 404) is handled
 * by {@link GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/admin")
public class RestaurantAdminController {

    private final RestaurantAdminUseCase adminUseCase;

    public RestaurantAdminController(RestaurantAdminUseCase adminUseCase) {
        this.adminUseCase = adminUseCase;
    }

    /**
     * Creates a new staff account within the authenticated admin's restaurant.
     *
     * <p>The tenant is resolved from the current thread's {@link TenantContextHolder}
     * (Req 8.1). Rejects requests with a duplicate username within the tenant with
     * 409 Conflict (Req 8.4).
     *
     * @param request body containing {@code username} and {@code role}
     * @return 201 Created with the new account details
     */
    @PostMapping("/staff")
    @ResponseStatus(HttpStatus.CREATED)
    public StaffAccountResponse createStaffAccount(@RequestBody CreateStaffAccountRequest request) {
        TenantId tenantId = TenantContextHolder.require();
        Role role = Role.valueOf(request.role());
        StaffAccount account = adminUseCase.createStaffAccount(tenantId, request.username(), role);
        return StaffAccountResponse.from(account);
    }

    /**
     * Deactivates the staff account identified by {@code accountId}.
     *
     * <p>Cross-tenant access is denied by the use case (Req 8.8); the account
     * must belong to the authenticated admin's tenant. Returns 404 if the account
     * does not exist or belongs to a different tenant.
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

    /**
     * Updates the restaurant configuration for the authenticated admin's tenant.
     *
     * <p>Returns 404 if no restaurant exists for the resolved tenant (Req 8.8).
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

    /**
     * Response body for {@code PUT /api/admin/config}.
     *
     * @param name the persisted display name of the restaurant
     */
    public record RestaurantConfigResponse(String name) {}
}
