package com.example.restaurant.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link RestaurantJpaEntity}.
 *
 * <p>The {@code restaurant} table's primary key is the {@code tenant_id} itself, so
 * each row is uniquely identified by its tenant. The tenant filter is not applied to
 * restaurant lookups because the restaurant IS the tenant root and must be queryable
 * by the platform admin (cross-tenant) as well as by tenant-scoped operations.
 */
public interface RestaurantJpaRepository
        extends JpaRepository<RestaurantJpaEntity, UUID> {

    /**
     * Finds the restaurant with the given unique business identifier (Req 9.5).
     *
     * @param identifier the unique business identifier
     * @return the restaurant entity, or empty if not found
     */
    Optional<RestaurantJpaEntity> findByIdentifier(String identifier);
}
