package org.readybell.notification.adapter.out.persistence;

import jakarta.persistence.*;
import org.readybell.notification.domain.Notification;
import org.readybell.notification.domain.NotificationId;
import org.readybell.notification.domain.NotificationStatus;
import org.readybell.shared.domain.TenantId;
import org.readybell.shared.domain.jpa.TenantScopedEntity;
import org.readybell.subscription.domain.DeviceSubscriptionId;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping the {@code notification} table.
 *
 * <p>Extends {@link TenantScopedEntity} so the Hibernate {@code tenantFilter} is applied
 * automatically and the {@code tenant_id} column is stamped on first persist
 * (Req 10.1, 10.2, 10.4, 10.6).
 *
 * <p>Table: {@code notification(id PK, tenant_id, subscription_id FK, status, payload, created_at)}
 *
 * <p>Index on {@code (tenant_id)} supports tenant-scoped notification lookups (ADR-007).
 */
@Entity
@Table(
        name = "notification",
        indexes = {
                @Index(name = "idx_notification_tenant_id", columnList = "tenant_id")
        }
)
public class NotificationJpaEntity extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "subscription_id", nullable = false, updatable = false)
    private UUID subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private NotificationStatus status;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Required by JPA. */
    protected NotificationJpaEntity() {}

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    /** Maps the domain aggregate to a JPA entity ready for insert or merge. */
    public static NotificationJpaEntity from(Notification notification) {
        var entity = new NotificationJpaEntity();
        entity.id             = notification.id().value();
        entity.tenantId       = notification.tenantId().value();
        entity.subscriptionId = notification.subscriptionId().value();
        entity.status         = notification.status();
        entity.payload        = notification.payload();
        entity.createdAt      = Instant.now();
        return entity;
    }

    /** Rebuilds the domain aggregate from this entity. */
    public Notification toDomain() {
        return Notification.reconstitute(
                new NotificationId(id),
                new TenantId(tenantId),
                new DeviceSubscriptionId(subscriptionId),
                status,
                payload);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(UUID subscriptionId) { this.subscriptionId = subscriptionId; }

    public NotificationStatus getStatus() { return status; }
    public void setStatus(NotificationStatus status) { this.status = status; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
