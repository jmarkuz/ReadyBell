package com.example.restaurant.adapter.out.persistence;

import jakarta.persistence.*;
import com.example.restaurant.domain.AccountStatus;
import com.example.restaurant.domain.Role;
import com.example.restaurant.domain.StaffAccount;
import com.example.restaurant.domain.StaffAccountId;
import com.example.shared.domain.TenantId;
import com.example.shared.domain.jpa.TenantScopedEntity;

import java.util.UUID;

/**
 * JPA entity mapping the {@code staff_account} table.
 *
 * <p>Extends {@link TenantScopedEntity} so the Hibernate tenant filter is applied and
 * the {@code tenant_id} column is stamped on first persist.
 *
 * <p>Table: {@code staff_account(id PK, tenant_id FK, username, role, status,
 * UNIQUE(tenant_id, username))} — Req 8.4
 *
 * <p>Index on {@code (tenant_id)} supports tenant-scoped staff lookups (ADR-007).
 */
@Entity
@Table(
        name = "staff_account",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_staff_account_tenant_username",
                        columnNames = {"tenant_id", "username"})       // Req 8.4
        },
        indexes = {
                @Index(name = "idx_staff_account_tenant_id", columnList = "tenant_id")
        }
)
public class StaffAccountJpaEntity extends TenantScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "username", nullable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccountStatus status;

    /** Required by JPA. */
    protected StaffAccountJpaEntity() {}

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    /** Maps the domain aggregate to a JPA entity ready for insert or merge. */
    public static StaffAccountJpaEntity from(StaffAccount account) {
        var entity = new StaffAccountJpaEntity();
        entity.id       = account.getId().value();
        entity.tenantId = account.getTenantId().value();
        entity.username = account.getUsername();
        entity.role     = account.getRole();
        entity.status   = account.getStatus();
        return entity;
    }

    /** Reconstructs the domain aggregate from this entity. */
    public StaffAccount toDomain() {
        return StaffAccount.reconstitute(
                new StaffAccountId(id),
                new TenantId(tenantId),
                username,
                role,
                status);
    }

    /** Returns the typed domain identity. */
    public StaffAccountId getDomainId() {
        return new StaffAccountId(id);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }
}
