package org.readybell.platformadmin.adapter.in.web;

import org.readybell.platformadmin.application.DuplicateRestaurantIdentifierException;
import org.readybell.platformadmin.application.PlatformAdminUseCase;
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
import java.util.List;

/**
 * Inbound REST adapter for the Platform Admin API.
 *
 * <p>Exposes tenant-management operations across all restaurants. Only principals
 * holding the platform-admin role may invoke these endpoints; non-admin
 * requests are denied by Spring Security in the composition root (task 21).
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

    // POST /api/platform/restaurants — create restaurant

    /** Creates a new Restaurant tenant with a unique, platform-assigned {@link TenantId}. */
    /** All restaurants, newest first by identifier (platform operator overview). */
    @GetMapping
    public List<RestaurantResponse> listRestaurants() {
        return platformAdminUseCase.listRestaurants().stream().map(RestaurantResponse::from).toList();
    }

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
        String weak = org.readybell.shared.domain.PasswordPolicy.violation(request.adminUsername(), request.adminPassword());
        if (weak != null) {
            throw new IllegalArgumentException(weak);       // answered as a 400 with this text
        }
        String hash = passwordEncoder.encode(request.adminPassword());
        // One transaction: a failure must not leave a restaurant without its admin (or the reverse)
        Restaurant restaurant = transaction.execute(status ->
                platformAdminUseCase.createRestaurant(request.identifier(), config, request.adminUsername(), hash));
        return RestaurantResponse.withAdmin(restaurant, UsernamePolicy.normalize(request.adminUsername()));
    }

    // PUT /api/platform/restaurants/{tenantId} — update restaurant

    /** Updates the configuration of an existing restaurant identified by its tenant id. */
    @PutMapping("/{tenantId}")
    public RestaurantResponse updateRestaurant(@PathVariable("tenantId") UUID tenantId,
                                               @RequestBody UpdateRestaurantRequest request) {
        TenantId tid = new TenantId(tenantId);
        RestaurantConfig config = RestaurantConfig.of(request.name());
        Restaurant restaurant = platformAdminUseCase.updateRestaurant(tid, config);
        return RestaurantResponse.from(restaurant);
    }

    // DELETE /api/platform/restaurants/{tenantId} — deactivate restaurant

    /** Deactivates the restaurant identified by its tenant id. */
    @DeleteMapping("/{tenantId}")
    public RestaurantResponse deactivateRestaurant(@PathVariable("tenantId") UUID tenantId) {
        TenantId tid = new TenantId(tenantId);
        Restaurant restaurant = platformAdminUseCase.deactivateRestaurant(tid);
        return RestaurantResponse.from(restaurant);
    }

    // Exception handlers (RFC 7807 Problem Details)

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

    /** Handles invalid input (blank identifier, null config field, etc.). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "https://readybell.org/problems/invalid-request",
                "Invalid Request",
                ex.getMessage()
        );
    }

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
