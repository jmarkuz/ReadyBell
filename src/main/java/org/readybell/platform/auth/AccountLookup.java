package org.readybell.platform.auth;

import org.readybell.platform.TenantUser;
import org.readybell.shared.domain.TenantId;

import java.util.Optional;

/**
 * Finds the account behind a token or a sign-in: a username is unique only within one restaurant, so the lookup
 * always names the tenant. {@code null} means a platform-level account (the operator), which has no tenant.
 */
@FunctionalInterface
public interface AccountLookup {
    /** The account, enabled or not (a disabled one exists but must not sign in); empty when there is none. */
    Optional<TenantUser> find(TenantId tenant, String username);
}
