package org.readybell.restaurant.domain;

import org.readybell.shared.domain.TenantId;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory fake implementation of {@link RestaurantRepository}.
 *
 * <p>Backed by a simple {@link HashMap}; intended for use in unit and property-based
 * tests that exercise application logic without any I/O. No Spring or JPA annotations.
 *
 * <p>{@link Restaurant} is the tenant root so both lookup keys are equally important:
 * {@link #findByTenantId} is used when a tenant context is already resolved, while
 * {@link #findByIdentifier} is used during QR-code resolution (Req 1.4, 1.7).
 */
public class InMemoryRestaurantRepository implements RestaurantRepository {

    /** Primary index: tenantId → Restaurant. */
    private final Map<TenantId, Restaurant> byTenantId = new HashMap<>();

    /** Secondary index: identifier → Restaurant. */
    private final Map<String, Restaurant> byIdentifier = new HashMap<>();

    @Override
    public void save(Restaurant restaurant) {
        byTenantId.put(restaurant.getTenantId(), restaurant);
        byIdentifier.put(restaurant.getIdentifier(), restaurant);
    }

    @Override
    public Optional<Restaurant> findByTenantId(TenantId tenantId) {
        return Optional.ofNullable(byTenantId.get(tenantId));
    }

    @Override
    public Optional<Restaurant> findByIdentifier(String identifier) {
        return Optional.ofNullable(byIdentifier.get(identifier));
    }

    @Override
    public java.util.List<Restaurant> findAll() {
        return java.util.List.copyOf(byTenantId.values());
    }

    /** Convenience helper — returns the total number of stored restaurants. */
    public int size() {
        return byTenantId.size();
    }

    /** Clears all stored restaurants. Useful for test set-up/tear-down. */
    public void clear() {
        byTenantId.clear();
        byIdentifier.clear();
    }
}
