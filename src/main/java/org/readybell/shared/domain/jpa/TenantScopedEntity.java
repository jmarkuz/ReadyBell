package org.readybell.shared.domain.jpa;

import org.readybell.shared.domain.TenantId;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.readybell.shared.tenancy.TenantContextHolder;

import java.util.UUID;

/**
 * JPA {@code @MappedSuperclass} that enforces tenant isolation at the persistence layer.
 *
 * <h3>Filter enforcement (Req 10.2, 10.4)</h3>
 * <p>The Hibernate named filter {@code tenantFilter} with condition
 * {@code tenant_id = :tenantId} is declared here and applied to every subclass table.
 * The filter is bound automatically around every persistence-adapter call by
 * {@code TenantFilterInterceptor} (tenant from the method's {@link TenantId} argument, else from
 * {@link TenantContextHolder}); methods that must see every tenant say so with {@link CrossTenant}.
 * Adapters never enable the filter by hand (Req 10.2, 10.3, 10.4).
 *
 * <h3>Write stamping (Req 10.1, 10.6)</h3>
 * <p>The {@link #stampTenant()} {@code @PrePersist} listener automatically stamps
 * {@link #tenantId} with the request-scoped tenant from {@link TenantContextHolder}
 * before any new entity row is committed, so tenant-scoped records always carry the
 * correct tenant discriminator (Req 10.6). If a {@code tenantId} was already set
 * (e.g., for the {@code RestaurantJpaEntity}
 * whose PK is the {@code tenant_id}), the field is left unchanged.
 *
 * <h3>ADR-004</h3>
 * <p>This class is intentionally the <em>only</em> place that couples Hibernate's
 * filter mechanism to the tenant-isolation contract so that switching the filter
 * strategy later affects a single file.
 */
@FilterDef(
        name = "tenantFilter",
        parameters = @ParamDef(name = "tenantId", type = UUID.class)
)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@MappedSuperclass
public abstract class TenantScopedEntity {

    /**
     * Tenant discriminator column present on every tenant-scoped table.
     *
     * <p>Never null, never updated after first insert ({@code updatable = false}).
     * Stamped automatically by {@link #stampTenant()} when the entity is first persisted.
     */
    @Column(name = "tenant_id", nullable = false, updatable = false)
    protected UUID tenantId;

    /**
     * JPA lifecycle callback — stamps the current request's tenant onto this entity
     * before the first {@code INSERT} is executed (Req 10.6).
     *
     * <p>If {@link #tenantId} is already set (e.g., because the subclass assigned it
     * explicitly in a factory method), it is left untouched so that the caller's
     * intent is preserved.
     */
    @PrePersist
    void stampTenant() {
        if (tenantId == null) {
            tenantId = TenantContextHolder.require().value(); // Req 10.6
        }
    }

    /** Returns the raw {@link UUID} tenant discriminator value. */
    public UUID getTenantId() {
        return tenantId;
    }
}
