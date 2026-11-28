package org.readybell.restaurant.domain;

import org.readybell.shared.domain.TenantId;

import java.util.Optional;
import java.util.List;

/**
 * Repository port for {@link Restaurant} aggregates.
 *
 * <p>This is an outbound port defined in the domain layer. Production
 * adapters (JPA) implement it in the adapter layer; in-memory fakes implement it in tests.
 */
public interface RestaurantRepository {
    void save(Restaurant restaurant);

    /** Finds the restaurant whose {@link TenantId} matches the given value. */
    Optional<Restaurant> findByTenantId(TenantId tenantId);

    /** Finds the restaurant with the given unique business identifier. */
    Optional<Restaurant> findByIdentifier(String identifier);

    /** Every restaurant on the platform (platform admin view). */
    List<Restaurant> findAll();
}
