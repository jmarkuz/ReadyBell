package org.readybell.ordering.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * The fact row of a finished order: timings, outcome and size, with no customer name, no item names and no link to a
 * person. Written by {@link JpaOrderTimelineRepository} (and by the retention backfill) with native SQL; the entity makes
 * the {@code dev} profile's Hibernate-built schema contain the table.
 */
@Entity
@Table(name = "order_fact", indexes = @Index(name = "idx_order_fact_tenant_created", columnList = "tenant_id,created_at"))
class OrderFactJpaEntity {
    @Id
    @Column(name = "order_id")
    UUID orderId;

    @Column(name = "tenant_id", nullable = false)
    UUID tenantId;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "in_progress_at")
    Instant inProgressAt;

    @Column(name = "ready_at")
    Instant readyAt;

    @Column(name = "finished_at", nullable = false)
    Instant finishedAt;

    @Column(name = "outcome", nullable = false, length = 16)
    String outcome;

    @Column(name = "item_count", nullable = false)
    int itemCount;

    @Column(name = "quantity", nullable = false)
    int quantity;

    @Column(name = "connected", nullable = false)
    boolean connected;

    @Column(name = "approximate", nullable = false)
    boolean approximate;
}
