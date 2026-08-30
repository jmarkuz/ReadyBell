package com.example.ordering.adapter.out.persistence;

import jakarta.persistence.*;
import com.example.ordering.domain.Order;
import com.example.ordering.domain.OrderId;
import com.example.ordering.domain.OrderStatus;
import com.example.session.domain.CustomerSessionId;
import com.example.shared.domain.TenantId;
import com.example.shared.domain.jpa.TenantScopedEntity;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping the {@code order} table.
 *
 * <p>Extends {@link TenantScopedEntity} so the Hibernate {@code tenantFilter} is applied
 * automatically and the {@code tenant_id} column is stamped on first persist
 * (Req 10.1, 10.2, 10.4, 10.6).
 *
 * <p>The {@code version} column is mapped with {@link Version} to support JPA optimistic
 * locking so at most one of two concurrent status transitions succeeds (Req 5.8).
 *
 * <p>Table: {@code order(id PK, tenant_id, session_id FK, status, version, created_at, updated_at)}
 *
 * <p>Indexes on {@code (tenant_id)} and {@code (tenant_id, session_id)} support
 * tenant-scoped order queries (ADR-007).
 */
@Entity
@Table(
        name = "\"order\"",  // "order" is a reserved SQL keyword; quote it
        indexes = {
                @Index(name = "idx_order_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_order_tenant_session", columnList = "tenant_id,session_id")
        }
)
public class OrderJpaEntity extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    /**
     * Optimistic-lock version mapped to the {@code version} column.
     *
     * <p>JPA increments this automatically on every successful flush and rejects a
     * flush whose stored version does not match, ensuring at most one concurrent
     * state transition succeeds (Req 5.8).
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA. */
    protected OrderJpaEntity() {}

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    /** Maps the domain aggregate to a JPA entity ready for insert or merge. */
    public static OrderJpaEntity from(Order order) {
        var now    = Instant.now();
        var entity = new OrderJpaEntity();
        entity.id        = order.getId().value();
        entity.tenantId  = order.getTenantId().value();
        entity.sessionId = order.getSessionId().value();
        entity.status    = order.getStatus();
        entity.version   = order.getVersion();
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    /** Rebuilds the domain aggregate from this entity. */
    public Order toDomain() {
        return Order.reconstitute(
                new OrderId(id),
                new TenantId(tenantId),
                new CustomerSessionId(sessionId),
                status,
                version);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
