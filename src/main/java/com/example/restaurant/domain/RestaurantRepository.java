package com.example.restaurant.domain;

import com.example.shared.domain.TenantId;

import java.util.Optional;

/**
 * Repository port for {@link Restaurant} aggregates.
 *
 * <p>This is an outbound port defined in the domain layer (Req 11.1, 11.2). Production
 * adapters (JPA) implement it in the adapter layer; in-memory fakes implement it in tests.
 *
 * <p>{@link Restaurant} is the tenant root; lookups by {@link TenantId} or by the
 * unique business {@link Restaurant#getIdentifier() identifier} are the two primary access
 * patterns used during session start (Req 1.4, 9.6) and tenant management (Req 9.2, 9.5).
 */
public interface RestaurantRepository {

    /**
     * Persists a new or updated {@link Restaurant}.
     *
     * @param restaurant the restaurant to save (must not be null)
     */
    void save(Restaurant restaurant);

    /**
     * Finds the restaurant whose {@link TenantId} matches the given value.
     *
     * <p>Because a {@link TenantId} uniquely identifies exactly one Restaurant, this
     * returns at most one result (Req 9.2).
     *
     * @param tenantId the tenant identifier to look up (must not be null)
     * @return the restaurant, or {@link Optional#empty()} if none exists with that id
     */
    Optional<Restaurant> findByTenantId(TenantId tenantId);

    /**
     * Finds the restaurant with the given unique business identifier (Req 9.5).
     *
     * <p>Used during session start to resolve a QR-code token to a tenant (Req 1.4, 1.7).
     *
     * @param identifier the unique business identifier encoded in the QR code (must not be null)
     * @return the restaurant, or {@link Optional#empty()} if none exists with that identifier
     */
    Optional<Restaurant> findByIdentifier(String identifier);
}
