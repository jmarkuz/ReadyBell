package org.readybell.restaurant.adapter.out.persistence;

import org.readybell.shared.domain.jpa.TenantScopedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.readybell.restaurant.domain.Restaurant;
import org.readybell.restaurant.domain.RestaurantConfig;
import org.readybell.restaurant.domain.RestaurantStatus;
import org.readybell.shared.domain.TenantId;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity mapping the {@code restaurant} table.
 *
 * <p>For the restaurant table, {@code tenant_id} is the primary key (the restaurant IS the
 * tenant). This entity does not extend {@link TenantScopedEntity}
 * because the PK already is the tenant discriminator; the Hibernate tenant filter is applied
 * by {@link JpaRestaurantRepository} adapter regardless.
 *
 * <p>Table: {@code restaurant(tenant_id PK, identifier UNIQUE, status, config_json, created_at)}
 *
 * <p>Indexes on {@code (tenant_id)} are implicitly covered by the PK. (Req 10.1, ADR-007)
 */
@Entity
@Table(
        name = "restaurant",
        indexes = {
                @Index(name = "idx_restaurant_identifier", columnList = "identifier")
        }
)
public class RestaurantJpaEntity {

    /** The restaurant's tenant_id serves as both PK and tenant discriminator. */
    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "identifier", nullable = false, unique = true)
    private String identifier;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RestaurantStatus status;

    /** Stores the JSON-serialised {@link RestaurantConfig} (kept simple as text). */
    /** Stored as a real JSON document ({@code {"name": "..."}}): jsonb on PostgreSQL. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_json")
    private Map<String, String> config;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Required by JPA. */
    protected RestaurantJpaEntity() {}

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    /** Maps the domain aggregate to a new JPA entity ready for insert or merge. */
    public static RestaurantJpaEntity from(Restaurant restaurant) {
        var entity = new RestaurantJpaEntity();
        entity.tenantId   = restaurant.getTenantId().value();
        entity.identifier = restaurant.getIdentifier();
        entity.status     = restaurant.getStatus();
        entity.config = new HashMap<>(Map.of("name", restaurant.getConfig().name()));
        entity.createdAt  = Instant.now();
        return entity;
    }

    /** Reconstructs the domain aggregate from this entity. */
    public Restaurant toDomain() {
        return Restaurant.reconstitute(
                new TenantId(tenantId),
                identifier,
                status,
                RestaurantConfig.of(config == null ? null : config.get("name")));
    }

    // -------------------------------------------------------------------------
    // Accessors (needed by the adapter)
    // -------------------------------------------------------------------------

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }

    public RestaurantStatus getStatus() { return status; }
    public void setStatus(RestaurantStatus status) { this.status = status; }

    public Map<String, String> getConfig() { return config; }
    public void setConfig(Map<String, String> config) { this.config = config; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
