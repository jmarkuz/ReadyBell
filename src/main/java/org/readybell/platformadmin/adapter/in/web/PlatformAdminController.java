package org.readybell.platformadmin.adapter.in.web;

import org.readybell.platformadmin.application.DuplicateRestaurantIdentifierException;
import org.readybell.platformadmin.application.PlatformAdminUseCase;
import org.readybell.platformadmin.application.RestaurantNotFoundException;
import jakarta.validation.Valid;
import org.readybell.restaurant.application.UsernamePolicy;
import org.readybell.restaurant.domain.Restaurant;
import org.readybell.restaurant.domain.RestaurantConfig;
import org.readybell.shared.domain.TenantId;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
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

    private static final MediaType PROBLEM_JSON =
            MediaType.parseMediaType("application/problem+json");

    private final PlatformAdminUseCase platformAdminUseCase;

    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transaction;

    public PlatformAdminController(PlatformAdminUseCase platformAdminUseCase,
                                   PasswordEncoder passwordEncoder,
                                   PlatformTransactionManager transactionManager) {
        this.platformAdminUseCase = platformAdminUseCase;
        this.passwordEncoder = passwordEncoder;
        this.transaction = new TransactionTemplate(transactionManager);
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
    public RestaurantResponse createRestaurant(@Valid @RequestBody CreateRestaurantRequest request) {
        RestaurantConfig config = RestaurantConfig.of(request.name());
        if (!request.hasAdmin()) {
            return RestaurantResponse.from(platformAdminUseCase.createRestaurant(request.identifier(), config));
        }
        if (request.adminUsername() == null || request.adminUsername().isBlank()
                || request.adminPassword() == null || request.adminPassword().isBlank()) {
            throw new IllegalArgumentException("adminUsername and adminPassword must be given together");
        }
        String hash = passwordEncoder.encode(request.adminPassword());
        // One transaction: a failure must not leave a restaurant without its admin (or the reverse)
        Restaurant restaurant = transaction.execute(status ->
                platformAdminUseCase.createRestaurant(request.identifier(), config, request.adminUsername(), hash));
        return RestaurantResponse.withAdmin(restaurant, UsernamePolicy.normalize(request.adminUsername()));
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
    public RestaurantResponse updateRestaurant(@PathVariable("tenantId") UUID tenantId,
                                               @RequestBody UpdateRestaurantRequest request) {
        TenantId tid = new TenantId(tenantId);
        RestaurantConfig config = RestaurantConfig.of(request.name());
        Restaurant restaurant = platformAdminUseCase.updateRestaurant(tid, config);
        return RestaurantResponse.from(restaurant);
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
    public RestaurantResponse deactivateRestaurant(@PathVariable("tenantId") UUID tenantId) {
        TenantId tid = new TenantId(tenantId);
        Restaurant restaurant = platformAdminUseCase.deactivateRestaurant(tid);
        return RestaurantResponse.from(restaurant);
    }

    // -------------------------------------------------------------------------
    // Exception handlers (RFC 7807 Problem Details)
    // -------------------------------------------------------------------------

    /**
     * Handles duplicate restaurant identifier on creation (Req 9.5).
     *
     * @return 409 Conflict with a problem detail identifying the duplicate identifier
     */
    @ExceptionHandler(DuplicateRestaurantIdentifierException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateIdentifier(
            DuplicateRestaurantIdentifierException ex) {
        return problem(
                HttpStatus.CONFLICT,
                "https://readybell.org/problems/duplicate-restaurant-identifier",
                "Duplicate Restaurant Identifier",
                ex.getMessage()
        );
    }

    /**
     * Handles a restaurant not found for platform admin operations (Req 9.3).
     *
     * @return 404 Not Found with a problem detail
     */
    @ExceptionHandler(RestaurantNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(RestaurantNotFoundException ex) {
        return problem(
                HttpStatus.NOT_FOUND,
                "https://readybell.org/problems/restaurant-not-found",
                "Restaurant Not Found",
                ex.getMessage()
        );
    }

    /**
     * Handles invalid input (blank identifier, null config field, etc.).
     *
     * @return 400 Bad Request with a problem detail describing the validation failure
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "https://readybell.org/problems/invalid-request",
                "Invalid Request",
                ex.getMessage()
        );
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private ResponseEntity<Map<String, Object>> problem(HttpStatus status,
                                                        String type,
                                                        String title,
                                                        String detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type",   type);
        body.put("title",  title);
        body.put("status", status.value());
        body.put("detail", detail);
        return ResponseEntity
                .status(status)
                .contentType(PROBLEM_JSON)
                .body(body);
    }
}
