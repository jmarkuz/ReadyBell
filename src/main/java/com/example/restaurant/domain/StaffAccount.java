package com.example.restaurant.domain;

import com.example.shared.domain.TenantId;

import java.util.Objects;

/**
 * Aggregate representing a staff member's account within a single restaurant tenant.
 *
 * <p>A {@code StaffAccount} is always scoped to one tenant; the combination of
 * {@code tenantId} and {@code username} must be unique within that tenant (Req 8.4).
 * New accounts are created in the {@link AccountStatus#ACTIVE} state (Req 8.3) and
 * may be {@linkplain #deactivate() deactivated} later (Req 8.5).
 *
 * <p>No public setters are exposed — all state transitions go through named
 * methods to enforce invariants.
 */
public class StaffAccount {

    private final StaffAccountId id;
    private final TenantId tenantId;
    private final String username;
    private final Role role;
    private AccountStatus status;

    private StaffAccount(StaffAccountId id, TenantId tenantId, String username, Role role, AccountStatus status) {
        this.id = id;
        this.tenantId = tenantId;
        this.username = username;
        this.role = role;
        this.status = status;
    }

    /**
     * Creates a new, active staff account for the given tenant (Req 8.3).
     *
     * @param tenantId the tenant this account belongs to
     * @param username the account's username, unique within the tenant (Req 8.4)
     * @param role     the role assigned to this account
     * @return a new {@code StaffAccount} with status {@link AccountStatus#ACTIVE}
     */
    public static StaffAccount create(TenantId tenantId, String username, Role role) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(role, "role must not be null");
        if (username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        return new StaffAccount(StaffAccountId.newId(), tenantId, username, role, AccountStatus.ACTIVE);
    }

    /**
     * Deactivates this account (Req 8.5).
     *
     * <p>Calling this method on an already-deactivated account is idempotent.
     */
    public void deactivate() {
        this.status = AccountStatus.DEACTIVATED;
    }

    /** Returns {@code true} if this account's status is {@link AccountStatus#ACTIVE}. */
    public boolean isActive() {
        return this.status == AccountStatus.ACTIVE;
    }

    public StaffAccountId getId() {
        return id;
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public String getUsername() {
        return username;
    }

    public Role getRole() {
        return role;
    }

    public AccountStatus getStatus() {
        return status;
    }

    // -------------------------------------------------------------------------
    // Reconstitution — used by the JPA adapter to rebuild the aggregate from a
    // stored entity without generating a new id.
    // -------------------------------------------------------------------------

    /**
     * Rebuilds a {@code StaffAccount} from its stored state.
     *
     * <p>Unlike {@link #create} this method uses the supplied {@code id} as-is
     * rather than generating a fresh one. Intended for use by the JPA adapter only.
     *
     * @param id       the persisted account identity
     * @param tenantId the persisted tenant
     * @param username the persisted username
     * @param role     the persisted role
     * @param status   the persisted account status
     * @return a staff account aggregate
     */
    public static StaffAccount reconstitute(StaffAccountId id,
                                            TenantId tenantId,
                                            String username,
                                            Role role,
                                            AccountStatus status) {
        return new StaffAccount(id, tenantId, username, role, status);
    }
}
