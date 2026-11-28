package org.readybell.restaurant.domain;

import org.readybell.shared.domain.TenantId;

import java.util.Optional;
import java.util.List;

/**
 * Repository port for {@link Restaurant} aggregates.
 *
 * <p>This is an outbound port defined in the domain layer. Production
 * adapters (JPA) implement it in the adapter layer; in-memory fakes implement it in tests.
 *
 * <p>{@link Restaurant} is the tenant root; lookups by {@link TenantId} or by the
 * unique business {@link Restaurant#getIdentifier() identifier} are the two primary access
 * patterns used during session start and tenant management.
 */
public interface RestaurantRepository {
    void save(Restaurant restaurant);

    /**
     * Finds the restaurant whose {@link TenantId} matches the given value.
     *
     * <p>Because a {@link TenantId} uniquely identifies exactly one Restaurant, this
     * returns at most one result.
     */
    Optional<Restaurant> findByTenantId(TenantId tenantId);

    /**
     * Finds the restaurant with the given unique business identifier.
     *
     * <p>Used during session start to resolve a QR-code token to a tenant.
     */
    Optional<Restaurant> findByIdentifier(String identifier);

    /** Every restaurant on the platform (platform admin view). */
    List<Restaurant> findAll();
}
