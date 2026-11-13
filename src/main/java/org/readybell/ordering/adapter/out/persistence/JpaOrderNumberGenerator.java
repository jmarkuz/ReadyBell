package org.readybell.ordering.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.readybell.ordering.domain.OrderNumberGenerator;
import org.readybell.shared.domain.TenantId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Allocates per-restaurant order numbers from the {@code order_counter} row.
 *
 * <p>One atomic upsert creates the counter on a restaurant's first order and increments it afterwards. The row lock
 * it takes is held until the surrounding transaction (the one that also inserts the order) ends, so concurrent
 * creations for the same restaurant are serialised (two first-ever orders included: the second waits on the
 * conflicting insert), numbers are consecutive and a rollback gives its number back. Counters of different
 * restaurants never contend.
 */
@Repository
@Transactional
public class JpaOrderNumberGenerator implements OrderNumberGenerator {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public int next(TenantId tenantId) {
        Number number = (Number) entityManager.createNativeQuery("""
                INSERT INTO order_counter (tenant_id, last_number) VALUES (:tenant, 1)
                ON CONFLICT (tenant_id) DO UPDATE SET last_number = order_counter.last_number + 1
                RETURNING last_number
                """).setParameter("tenant", tenantId.value()).getSingleResult();
        return number.intValue();
    }
}
