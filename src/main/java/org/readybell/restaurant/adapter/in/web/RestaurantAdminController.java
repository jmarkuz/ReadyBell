package org.readybell.restaurant.adapter.in.web;

import jakarta.validation.Valid;

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

    /** Creates a new staff account within the authenticated admin's restaurant. */
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

    /** Deactivates the staff account identified by {@code accountId}. */
    @DeleteMapping("/staff/{accountId}")
    public StaffAccountResponse deactivateStaffAccount(@PathVariable("accountId") UUID accountId) {
        TenantId tenantId = TenantContextHolder.require();
        StaffAccountId staffAccountId = new StaffAccountId(accountId);
        StaffAccount account = adminUseCase.deactivateStaffAccount(tenantId, staffAccountId);
        return StaffAccountResponse.from(account);
    }

    /** Updates the restaurant configuration for the authenticated admin's tenant. */
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
