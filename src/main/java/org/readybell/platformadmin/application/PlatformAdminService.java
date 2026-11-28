package org.readybell.platformadmin.application;

import org.readybell.restaurant.application.UsernamePolicy;
import org.readybell.restaurant.application.RestaurantNotFoundException;
import org.readybell.restaurant.domain.Restaurant;
import org.readybell.restaurant.domain.RestaurantConfig;
import org.readybell.restaurant.domain.RestaurantRepository;
import org.readybell.restaurant.domain.Role;
import org.readybell.restaurant.domain.StaffAccount;
import org.readybell.restaurant.domain.StaffAccountRepository;
import org.readybell.shared.domain.TenantId;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Application service implementing {@link PlatformAdminUseCase}.
 *
 * <p>This class is intentionally free of framework annotations; wiring is handled
 * at the configuration layer.
 *
 * <p><b>Access control:</b> Only Platform Admins are authorised to invoke
 * the operations on this service. Enforcement is delegated to the HTTP adapter layer
 * via Spring Security role-based access control (task 20/21).
 */
public class PlatformAdminService implements PlatformAdminUseCase {
    private final RestaurantRepository restaurantRepository;
    private final StaffAccountRepository staffRepository;

    public PlatformAdminService(RestaurantRepository restaurantRepository,
                                StaffAccountRepository staffRepository) {
        this.restaurantRepository = restaurantRepository;
        this.staffRepository = staffRepository;
    }

    @Override
    public Restaurant createRestaurant(String identifier, RestaurantConfig config,
                                       String adminUsername, String adminPasswordHash) {
        String name = UsernamePolicy.normalize(adminUsername);
        if (adminPasswordHash == null || adminPasswordHash.isBlank()) {
            throw new IllegalArgumentException("adminPasswordHash must not be blank");
        }
        // The restaurant is new, so its first admin's username cannot be taken there; the identifier check below
        Restaurant restaurant = createRestaurant(identifier, config);
        staffRepository.save(StaffAccount.create(restaurant.getTenantId(), name, Role.RESTAURANT_ADMIN, adminPasswordHash));
        return restaurant;
    }

    @Override
    public Restaurant createRestaurant(String identifier, RestaurantConfig config) {
        //  — identifier must be present and non-blank
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("identifier must not be null or blank");
        }

        //  — identifier must be unique; reject and persist nothing on collision
        if (restaurantRepository.findByIdentifier(identifier).isPresent()) {
            throw new DuplicateRestaurantIdentifierException(identifier);
        }

        TenantId tenantId = new TenantId(UUID.randomUUID());

        Restaurant restaurant = Restaurant.create(tenantId, identifier, config);
        restaurantRepository.save(restaurant);
        return restaurant;
    }

    @Override
    public Restaurant updateRestaurant(TenantId tenantId, RestaurantConfig config) {
        Restaurant restaurant = restaurantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new RestaurantNotFoundException(tenantId));

        restaurant.updateConfig(config);
        restaurantRepository.save(restaurant);
        return restaurant;
    }

    @Override
    public List<Restaurant> listRestaurants() {
        return restaurantRepository.findAll().stream()
                .sorted(Comparator.comparing(Restaurant::getIdentifier))
                .toList();
    }

    @Override
    public Restaurant deactivateRestaurant(TenantId tenantId) {
        Restaurant restaurant = restaurantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new RestaurantNotFoundException(tenantId));

        restaurant.deactivate();
        restaurantRepository.save(restaurant);
        return restaurant;
    }
}
