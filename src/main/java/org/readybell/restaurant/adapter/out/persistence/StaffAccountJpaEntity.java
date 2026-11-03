package org.readybell.restaurant.adapter.out.persistence;

import jakarta.persistence.*;
import org.readybell.restaurant.domain.AccountStatus;
import org.readybell.restaurant.domain.Role;
import org.readybell.restaurant.domain.StaffAccount;
import org.readybell.restaurant.domain.StaffAccountId;
import org.readybell.shared.domain.TenantId;
import org.readybell.shared.domain.jpa.TenantScopedEntity;

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
                // Sign-in takes only a username, so it must identify one account platform-wide (Req 8.4)
                @UniqueConstraint(
                        name = "uq_staff_account_username",
                        columnNames = {"username"})
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

    /** Encoded password; null for accounts created before sign-in existed (they cannot sign in). */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

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
        entity.passwordHash = account.getPasswordHash();
        return entity;
    }

    /** Reconstructs the domain aggregate from this entity. */
    public StaffAccount toDomain() {
        return StaffAccount.reconstitute(
                new StaffAccountId(id),
                new TenantId(tenantId),
                username,
                role,
                status,
                passwordHash);
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

    public String getPasswordHash() { return passwordHash; }
    public void setStatus(AccountStatus status) { this.status = status; }
}
