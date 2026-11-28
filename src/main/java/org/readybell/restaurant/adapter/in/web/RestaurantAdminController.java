package org.readybell.restaurant.adapter.in.web;

import jakarta.validation.Valid;

import org.readybell.platform.GlobalExceptionHandler;
import org.readybell.restaurant.application.DuplicateUsernameException;
import org.readybell.restaurant.application.RestaurantNotFoundException;
import org.readybell.restaurant.application.StaffAccountNotFoundException;
import org.readybell.restaurant.application.RestaurantAdminUseCase;
import org.readybell.restaurant.domain.Restaurant;
import org.readybell.restaurant.domain.RestaurantConfig;
import org.readybell.restaurant.domain.Role;
import org.readybell.restaurant.domain.StaffAccount;
import org.readybell.restaurant.domain.StaffAccountId;
import org.readybell.shared.domain.TenantId;
import org.readybell.shared.tenancy.TenantContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Inbound REST adapter for the Restaurant Admin API.
 *
 * <p>All operations are tenant-scoped: the tenant is resolved from the current
 * authentication context via {@link TenantContextHolder#require()}, granting access
 * exclusively to records matching that tenant.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST   /api/admin/staff}             — create a staff account</li>
 *   <li>{@code DELETE /api/admin/staff/{accountId}} — deactivate a staff account</li>
 *   <li>{@code PUT    /api/admin/config}             — update restaurant config</li>
 * </ul>
 *
 * <p>Authentication guards are enforced by Spring Security in the
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

    private final PasswordEncoder passwordEncoder;

    public RestaurantAdminController(RestaurantAdminUseCase adminUseCase, PasswordEncoder passwordEncoder) {
        this.adminUseCase = adminUseCase;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates a new staff account within the authenticated admin's restaurant.
     *
     * <p>The tenant is resolved from the current thread's {@link TenantContextHolder}. Rejects requests with a duplicate username within the tenant with
     * 409 Conflict.
     */
    @GetMapping("/staff")
    public List<StaffAccountResponse> listStaff() {
        TenantId tenantId = TenantContextHolder.require();
        return adminUseCase.listStaffAccounts(tenantId).stream().map(StaffAccountResponse::from).toList();
    }

    @PostMapping("/staff")
    @ResponseStatus(HttpStatus.CREATED)
    public StaffAccountResponse createStaffAccount(@Valid @RequestBody CreateStaffAccountRequest request) {
        TenantId tenantId = TenantContextHolder.require();
        Role role = Role.valueOf(request.role());
        rejectWeakPassword(request.username(), request.password());
        StaffAccount account = adminUseCase.createStaffAccount(
                tenantId, request.username(), role, passwordEncoder.encode(request.password()));
        return StaffAccountResponse.from(account);
    }

    private static void rejectWeakPassword(String username, String password) {
        String problem = org.readybell.shared.domain.PasswordPolicy.violation(username, password);
        if (problem != null) {
            throw new IllegalArgumentException(problem);     // answered as a 400 with this text
        }
    }

    /**
     * Deactivates the staff account identified by {@code accountId}.
     *
     * <p>Cross-tenant access is denied by the use case; the account
     * must belong to the authenticated admin's tenant. Returns 404 if the account
     * does not exist or belongs to a different tenant.
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
     * <p>Returns 404 if no restaurant exists for the resolved tenant.
     */
    @PutMapping("/config")
    public RestaurantConfigResponse updateConfig(@Valid @RequestBody UpdateConfigRequest request) {
        TenantId tenantId = TenantContextHolder.require();
        RestaurantConfig config = RestaurantConfig.of(request.name());
        Restaurant restaurant = adminUseCase.updateRestaurantConfig(tenantId, config);
        return new RestaurantConfigResponse(restaurant.getConfig().name());
    }

    /** Response body for {@code PUT /api/admin/config}. */
    public record RestaurantConfigResponse(String name) {}
}
