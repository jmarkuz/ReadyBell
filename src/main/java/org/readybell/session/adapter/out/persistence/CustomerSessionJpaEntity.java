package org.readybell.session.adapter.out.persistence;

import jakarta.persistence.*;
import org.readybell.session.domain.CustomerSession;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.session.domain.SessionStatus;
import org.readybell.shared.domain.TenantId;
import org.readybell.shared.domain.jpa.TenantScopedEntity;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping the {@code customer_session} table.
 *
 * <p>Extends {@link TenantScopedEntity} so the Hibernate {@code tenantFilter} is applied
 * automatically to every query on this table, and the {@code tenant_id} column is stamped
 * on first persist (Req 10.1, 10.2, 10.4, 10.6).
 *
 * <p>Table: {@code customer_session(id PK, tenant_id, status, started_at)}
 *
 * <p>Index on {@code (tenant_id)} supports tenant-scoped session lookups (ADR-007).
 */
@Entity
@Table(
        name = "customer_session",
        indexes = {
                @Index(name = "idx_customer_session_tenant_id", columnList = "tenant_id")
        }
)
public class CustomerSessionJpaEntity extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SessionStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    /** Required by JPA. */
    protected CustomerSessionJpaEntity() {}

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    /** Maps the domain aggregate to a JPA entity ready for insert or merge. */
    public static CustomerSessionJpaEntity from(CustomerSession session) {
        var entity = new CustomerSessionJpaEntity();
        entity.id        = session.getId().value();
        entity.tenantId  = session.getTenantId().value();
        entity.status    = session.getStatus();
        entity.startedAt = session.getStartedAt();
        return entity;
    }

    /** Reconstructs the domain aggregate from this entity. */
    public CustomerSession toDomain() {
        return CustomerSession.reconstitute(
                new CustomerSessionId(id),
                new TenantId(tenantId),
                status,
                startedAt);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
}
