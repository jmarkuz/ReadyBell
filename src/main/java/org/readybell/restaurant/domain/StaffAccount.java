package org.readybell.restaurant.domain;

import org.readybell.shared.domain.TenantId;

import java.util.Objects;

/**
 * Aggregate representing a staff member's account within a single restaurant tenant.
 *
 * <p>A {@code StaffAccount} is always scoped to one tenant; the combination of
 * {@code tenantId} and {@code username} must be unique within that tenant.
 * New accounts are created in the {@link AccountStatus#ACTIVE} state and
 * may be {@linkplain #deactivate() deactivated} later.
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
    private final String passwordHash;
    private java.time.Instant deactivatedAt;

    private StaffAccount(StaffAccountId id, TenantId tenantId, String username, Role role, AccountStatus status,
                         String passwordHash) {
        this.id = id;
        this.tenantId = tenantId;
        this.username = username;
        this.role = role;
        this.status = status;
        this.passwordHash = passwordHash;
    }

    /** Creates a new, active staff account for the given tenant. */
    /** Creates an account that cannot sign in yet (no password set). */
    public static StaffAccount create(TenantId tenantId, String username, Role role) {
        return create(tenantId, username, role, null);
    }

    public static StaffAccount create(TenantId tenantId, String username, Role role, String passwordHash) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(role, "role must not be null");
        if (username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        return new StaffAccount(StaffAccountId.newId(), tenantId, username, role, AccountStatus.ACTIVE, passwordHash);
    }

    /**
     * Deactivates this account.
     *
     * <p>Calling this method on an already-deactivated account is idempotent.
     */
    public void deactivate() {
        deactivate(java.time.Instant.now());
    }

    /** Deactivates the account at the given instant (kept for the retention of personal data). */
    public void deactivate(java.time.Instant at) {
        if (this.status != AccountStatus.DEACTIVATED) {
            this.status = AccountStatus.DEACTIVATED;
            this.deactivatedAt = at;
        }
    }

    public java.time.Instant getDeactivatedAt() {
        return deactivatedAt;
    }

    public boolean isActive() {
        return this.status == AccountStatus.ACTIVE;
    }

    /** True when the account is active and has a password, i.e. may sign in. */
    public boolean canSignIn() {
        return isActive() && passwordHash != null && !passwordHash.isBlank();
    }

    public String getPasswordHash() {
        return passwordHash;
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

    // Reconstitution — used by the JPA adapter to rebuild the aggregate from a

    /**
     * Rebuilds a {@code StaffAccount} from its stored state.
     *
     * <p>Unlike {@link #create} this method uses the supplied {@code id} as-is
     * rather than generating a fresh one. Intended for use by the JPA adapter only.
     */
    public static StaffAccount reconstitute(StaffAccountId id,
                                            TenantId tenantId,
                                            String username,
                                            Role role,
                                            AccountStatus status) {
        return reconstitute(id, tenantId, username, role, status, null);
    }

    public static StaffAccount reconstitute(StaffAccountId id,
                                            TenantId tenantId,
                                            String username,
                                            Role role,
                                            AccountStatus status,
                                            String passwordHash) {
        return new StaffAccount(id, tenantId, username, role, status, passwordHash);
    }

    public static StaffAccount reconstitute(StaffAccountId id,
                                            TenantId tenantId,
                                            String username,
                                            Role role,
                                            AccountStatus status,
                                            String passwordHash,
                                            java.time.Instant deactivatedAt) {
        StaffAccount account = new StaffAccount(id, tenantId, username, role, status, passwordHash);
        account.deactivatedAt = deactivatedAt;
        return account;
    }
}
