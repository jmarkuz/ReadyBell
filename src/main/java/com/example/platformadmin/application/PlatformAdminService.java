package com.example.platformadmin.application;

import com.example.restaurant.domain.Restaurant;
import com.example.restaurant.domain.RestaurantConfig;
import com.example.restaurant.domain.RestaurantRepository;
import com.example.shared.domain.TenantId;

import java.util.UUID;

/**
 * Application service implementing {@link PlatformAdminUseCase}.
 *
 * <p>This class is intentionally free of framework annotations; wiring is handled
 * at the configuration layer.
 *
 * <p><b>Access control (Req 9.4):</b> Only Platform Admins are authorised to invoke
 * the operations on this service. Enforcement is delegated to the HTTP adapter layer
 * via Spring Security role-based access control (task 20/21).
 */
public class PlatformAdminService implements PlatformAdminUseCase {

    private final RestaurantRepository restaurantRepository;

    public PlatformAdminService(RestaurantRepository restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
    }

    /**
     * {@inheritDoc}
     *
     * <p>A new {@link TenantId} backed by a random UUID is assigned at creation
     * time (Req 9.2). The {@code identifier} must be unique across all tenants
     * (Req 9.5); if it is not, creation is rejected and nothing is persisted.
     */
    @Override
    public Restaurant createRestaurant(String identifier, RestaurantConfig config) {
        // Req 9.5 — identifier must be present and non-blank
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("identifier must not be null or blank");
        }

        // Req 9.5 — identifier must be unique; reject and persist nothing on collision
        if (restaurantRepository.findByIdentifier(identifier).isPresent()) {
            throw new DuplicateRestaurantIdentifierException(identifier);
        }

        // Req 9.2 — assign a platform-generated unique TenantId
        TenantId tenantId = new TenantId(UUID.randomUUID());

        Restaurant restaurant = Restaurant.create(tenantId, identifier, config);
        restaurantRepository.save(restaurant);
        return restaurant;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Restaurant updateRestaurant(TenantId tenantId, RestaurantConfig config) {
        Restaurant restaurant = restaurantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new RestaurantNotFoundException(tenantId));

        restaurant.updateConfig(config);
        restaurantRepository.save(restaurant);
        return restaurant;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Deactivation prevents new customer sessions from being opened against
     * this tenant (Req 9.3, 9.6).
     */
    @Override
    public Restaurant deactivateRestaurant(TenantId tenantId) {
        Restaurant restaurant = restaurantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new RestaurantNotFoundException(tenantId));

        restaurant.deactivate();
        restaurantRepository.save(restaurant);
        return restaurant;
    }
}
