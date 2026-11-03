package org.readybell.platform;

import org.readybell.shared.domain.TenantId;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * Authenticated principal bound to exactly one tenant.
 *
 * <p>The tenant is taken from server-side configuration, never from the request, so a caller
 * cannot choose which restaurant's data they operate on (Req 10.2, 10.4). Platform admins
 * have no tenant ({@link #getTenantId()} is {@code null}) and may only use
 * {@code /api/platform/**}.
 */
public class TenantUser extends User {

    private final TenantId tenantId;

    public TenantUser(String username,
                      String password,
                      Collection<? extends GrantedAuthority> authorities,
                      TenantId tenantId) {
        super(username, password, authorities);
        this.tenantId = tenantId;
    }

    /** The tenant this user belongs to, or {@code null} for platform-level users. */
    public TenantId getTenantId() {
        return tenantId;
    }
}
