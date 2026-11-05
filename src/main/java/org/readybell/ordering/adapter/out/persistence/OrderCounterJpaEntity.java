package org.readybell.ordering.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * One row per restaurant holding the last order number handed out. Mapped only so the schema is generated and
 * validated; it is read and written exclusively through {@link JpaOrderNumberGenerator}'s native statements.
 * Deliberately not tenant-scoped: the row is keyed by tenant and only ever addressed by that key.
 */
@Entity
@Table(name = "order_counter")
public class OrderCounterJpaEntity {

    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false)
    UUID tenantId;

    @Column(name = "last_number", nullable = false)
    int lastNumber;

    /** Required by JPA. */
    protected OrderCounterJpaEntity() {}
}
