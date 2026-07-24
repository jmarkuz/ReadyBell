package org.readybell.platformadmin.adapter.in.web;

import org.readybell.platformadmin.application.PlatformAdminUseCase;
import org.readybell.restaurant.domain.Restaurant;
import org.readybell.restaurant.domain.RestaurantConfig;
import org.readybell.shared.domain.TenantId;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Inbound REST adapter for the Platform Admin API (Req 9.1, 9.3, 9.4).
 *
 * <p>Exposes tenant-management operations across all restaurants. Only principals
 * holding the platform-admin role may invoke these endpoints (Req 9.4); non-admin
 * requests are denied by Spring Security in the composition root (task 21).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST   /api/platform/restaurants}            — create a restaurant tenant (Req 9.2, 9.5)</li>
 *   <li>{@code PUT    /api/platform/restaurants/{tenantId}} — update restaurant config (Req 9.3)</li>
 *   <li>{@code DELETE /api/platform/restaurants/{tenantId}} — deactivate a restaurant (Req 9.3, 9.6)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/platform/restaurants")
public class PlatformAdminController {

    private final PlatformAdminUseCase platformAdminUseCase;

    public PlatformAdminController(PlatformAdminUseCase platformAdminUseCase) {
        this.platformAdminUseCase = platformAdminUseCase;
    }

    // -------------------------------------------------------------------------
    // POST /api/platform/restaurants — create restaurant (Req 9.2, 9.5)
    // -------------------------------------------------------------------------

    /**
     * Creates a new Restaurant tenant with a unique, platform-assigned {@link TenantId}.
     *
     * @param request body containing {@code identifier} and {@code name}
     * @return 201 Created with the new tenant details
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateRestaurantResponse createRestaurant(@RequestBody CreateRestaurantRequest request) {
        RestaurantConfig config = RestaurantConfig.of(request.name());
        Restaurant restaurant = platformAdminUseCase.createRestaurant(request.identifier(), config);
        return new CreateRestaurantResponse(
                restaurant.getTenantId().value().toString(),
                restaurant.getIdentifier(),
                restaurant.getStatus().name()
        );
    }

    // -------------------------------------------------------------------------
    // PUT /api/platform/restaurants/{tenantId} — update restaurant (Req 9.3)
    // -------------------------------------------------------------------------

    /**
     * Updates the configuration of an existing restaurant identified by its tenant id.
     *
     * @param tenantId the UUID path variable of the tenant to update
     * @param request  body containing the new {@code name}
     * @return 200 OK with the updated restaurant details
     */
    @PutMapping("/{tenantId}")
    public CreateRestaurantResponse updateRestaurant(@PathVariable("tenantId") UUID tenantId,
                                                     @RequestBody UpdateRestaurantRequest request) {
        TenantId tid = new TenantId(tenantId);
        RestaurantConfig config = RestaurantConfig.of(request.name());
        Restaurant restaurant = platformAdminUseCase.updateRestaurant(tid, config);
        return new CreateRestaurantResponse(
                restaurant.getTenantId().value().toString(),
                restaurant.getIdentifier(),
                restaurant.getStatus().name()
        );
    }

    // -------------------------------------------------------------------------
    // DELETE /api/platform/restaurants/{tenantId} — deactivate restaurant (Req 9.3, 9.6)
    // -------------------------------------------------------------------------

    /**
     * Deactivates the restaurant identified by its tenant id.
     *
     * <p>After deactivation, new customer sessions for this restaurant are rejected
     * (Req 9.6).
     *
     * @param tenantId the UUID path variable of the tenant to deactivate
     * @return 200 OK with the deactivated restaurant details
     */
    @DeleteMapping("/{tenantId}")
    public CreateRestaurantResponse deactivateRestaurant(@PathVariable("tenantId") UUID tenantId) {
        TenantId tid = new TenantId(tenantId);
        Restaurant restaurant = platformAdminUseCase.deactivateRestaurant(tid);
        return new CreateRestaurantResponse(
                restaurant.getTenantId().value().toString(),
                restaurant.getIdentifier(),
                restaurant.getStatus().name()
        );
    }

    // -------------------------------------------------------------------------
    // Request / Response records
    // -------------------------------------------------------------------------

    /**
     * Request body for {@code POST /api/platform/restaurants}.
     *
     * @param identifier the unique business identifier for the new restaurant (Req 9.5)
     * @param name       the display name for the restaurant
     */
    public record CreateRestaurantRequest(String identifier, String name) {}

    /**
     * Request body for {@code PUT /api/platform/restaurants/{tenantId}}.
     *
     * @param name the new display name for the restaurant
     */
    public record UpdateRestaurantRequest(String name) {}

    /**
     * Response body for restaurant CRUD operations (Req 9.2).
     *
     * @param tenantId   the platform-assigned unique tenant identifier
     * @param identifier the business identifier of the restaurant
     * @param status     the current restaurant status ({@code ACTIVE} or {@code DEACTIVATED})
     */
    public record CreateRestaurantResponse(String tenantId, String identifier, String status) {}
}
