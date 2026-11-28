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
 * automatically and the {@code tenant_id} column is stamped on first persist.
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

    @Column(name = "order_id", updatable = false)
    private UUID orderId;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "url", length = 512)
    private String url;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    /** Set by the worker's claim query only; a plain save clears it, which is what releases the lease. */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_error", length = 255)
    private String lastError;

    @Column(name = "kind", nullable = false, length = 16)
    private String kind = Notification.KIND_READY;

    protected NotificationJpaEntity() {}

    /** Maps the domain aggregate to a JPA entity ready for insert or merge. */
    public static NotificationJpaEntity from(Notification notification) {
        var entity = new NotificationJpaEntity();
        entity.id             = notification.id().value();
        entity.tenantId       = notification.tenantId().value();
        entity.subscriptionId = notification.subscriptionId().value();
        entity.status         = notification.status();
        entity.payload        = notification.payload();
        entity.createdAt      = Instant.now();
        entity.orderId        = notification.orderId();
        entity.kind           = notification.kind();
        entity.title          = notification.title();
        entity.url            = notification.url();
        entity.readyAt        = notification.readyAt();
        entity.attempts       = notification.attempts();
        entity.nextAttemptAt  = notification.nextAttemptAt();
        entity.expiresAt      = notification.expiresAt();
        entity.lastError      = notification.lastError() == null ? null
                : notification.lastError().substring(0, Math.min(255, notification.lastError().length()));
        return entity;
    }

    public Notification toDomain() {
        return Notification.reconstitute(
                new NotificationId(id),
                new TenantId(tenantId),
                new DeviceSubscriptionId(subscriptionId),
                status,
                payload,
                orderId,
                title, url, readyAt, attempts, nextAttemptAt, expiresAt, lastError, kind);
    }

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
