package org.readybell.restaurant.adapter.out.persistence;

import org.readybell.restaurant.domain.Restaurant;
import org.readybell.restaurant.domain.RestaurantRepository;
import org.readybell.shared.domain.TenantId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * JPA adapter implementing the {@link RestaurantRepository} port.
 *
 * <p>The {@code restaurant} table uses {@code tenant_id} as its primary key so the
 * Hibernate tenant filter is not applied here — the restaurant entity IS the tenant
 * root and must be loadable both by platform admins (cross-tenant) and by the session
 * start flow (lookup by identifier). Cross-tenant isolation is enforced at the
 * application service level for restaurant management operations (Req 10.3).
 */
@Repository
@Transactional
public class JpaRestaurantRepository implements RestaurantRepository {

    private final RestaurantJpaRepository springRepo;

    public JpaRestaurantRepository(RestaurantJpaRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public void save(Restaurant restaurant) {
        var entity = RestaurantJpaEntity.from(restaurant);
        springRepo.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Restaurant> findByTenantId(TenantId tenantId) {
        return springRepo.findById(tenantId.value())
                .map(RestaurantJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Restaurant> findByIdentifier(String identifier) {
        return springRepo.findByIdentifier(identifier)
                .map(RestaurantJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<Restaurant> findAll() {
        return springRepo.findAll().stream().map(RestaurantJpaEntity::toDomain).toList();
    }
}
