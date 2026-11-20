package org.readybell.ordering.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One status change of an order. Rows are appended by {@link JpaOrderTimelineRepository} with native SQL; the entity
 * exists so that the schema of the {@code dev} profile (built by Hibernate) has the table, as Flyway has it elsewhere.
 * Not tenant-scoped: it is system history that analytics reads across tenants.
 */
@Entity
@Table(name = "order_status_history", indexes = {
        @Index(name = "idx_order_status_history_order", columnList = "order_id,at"),
        @Index(name = "idx_order_status_history_tenant_at", columnList = "tenant_id,at")})
class OrderStatusHistoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "order_id", nullable = false)
    UUID orderId;

    @Column(name = "tenant_id", nullable = false)
    UUID tenantId;

    @Column(name = "from_status", length = 32)
    String fromStatus;

    @Column(name = "to_status", nullable = false, length = 32)
    String toStatus;

    @Column(name = "at", nullable = false)
    Instant at;
}
