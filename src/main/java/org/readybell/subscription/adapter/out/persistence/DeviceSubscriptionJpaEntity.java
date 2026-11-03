package org.readybell.subscription.adapter.out.persistence;

import jakarta.persistence.*;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.TenantId;
import org.readybell.shared.domain.jpa.TenantScopedEntity;
import org.readybell.subscription.domain.DeviceSubscription;
import org.readybell.subscription.domain.DeviceSubscriptionId;
import org.readybell.subscription.domain.PushEndpoint;

import java.util.UUID;

/**
 * JPA entity mapping the {@code device_subscription} table.
 *
 * <p>Extends {@link TenantScopedEntity} so the Hibernate {@code tenantFilter} is applied
 * automatically and the {@code tenant_id} column is stamped on first persist
 * (Req 10.1, 10.2, 10.4, 10.6).
 *
 * <p>The unique constraint on {@code (tenant_id, session_id, endpoint_url)} enforces
 * idempotent re-registration at the database level (Req 3.4).
 *
 * <p>Table: {@code device_subscription(id PK, tenant_id, session_id FK, endpoint_url,
 * p256dh_key, auth_key, UNIQUE(tenant_id, session_id, endpoint_url))}
 *
 * <p>Indexes on {@code (tenant_id)} and {@code (tenant_id, session_id)} support
 * tenant-scoped subscription lookups (ADR-007).
 */
@Entity
@Table(
        name = "device_subscription",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_device_subscription_tenant_session_endpoint",
                        columnNames = {"tenant_id", "session_id", "endpoint_url"})  // Req 3.4
        },
        indexes = {
                @Index(name = "idx_device_subscription_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_device_subscription_tenant_session",
                        columnList = "tenant_id,session_id")
        }
)
public class DeviceSubscriptionJpaEntity extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "endpoint_url", nullable = false)
    private String endpointUrl;

    @Column(name = "p256dh_key", nullable = false)
    private String p256dhKey;

    @Column(name = "auth_key", nullable = false)
    private String authKey;

    /** Required by JPA. */
    protected DeviceSubscriptionJpaEntity() {}

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    /** Maps the domain aggregate to a JPA entity ready for insert or merge. */
    public static DeviceSubscriptionJpaEntity from(DeviceSubscription subscription) {
        var entity = new DeviceSubscriptionJpaEntity();
        entity.id          = subscription.getId().value();
        entity.tenantId    = subscription.getTenantId().value();
        entity.sessionId   = subscription.getSessionId().value();
        entity.endpointUrl = subscription.getEndpoint().endpointUrl();
        entity.p256dhKey   = subscription.getEndpoint().p256dhKey();
        entity.authKey     = subscription.getEndpoint().authKey();
        return entity;
    }

    /** Rebuilds the domain aggregate from this entity. */
    public DeviceSubscription toDomain() {
        return DeviceSubscription.reconstitute(
                new DeviceSubscriptionId(id),
                new TenantId(tenantId),
                new CustomerSessionId(sessionId),
                new PushEndpoint(endpointUrl, p256dhKey, authKey));
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }

    public String getP256dhKey() { return p256dhKey; }
    public void setP256dhKey(String p256dhKey) { this.p256dhKey = p256dhKey; }

    public String getAuthKey() { return authKey; }
    public void setAuthKey(String authKey) { this.authKey = authKey; }
}
