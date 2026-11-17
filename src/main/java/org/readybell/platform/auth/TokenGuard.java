package org.readybell.platform.auth;

import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;

/** Decoder of the staff access tokens: signature and expiry, then sign-out/rotation state, then the account's status. */
public class TokenGuard {

    private final JwtService jwt;
    private final TokenSessions sessions;
    private final AccountGuard accounts;

    public TokenGuard(JwtService jwt, TokenSessions sessions, AccountGuard accounts) {
        this.jwt = jwt;
        this.sessions = sessions;
        this.accounts = accounts;
    }

    public Jwt decode(String token) {
        Jwt decoded = jwt.decode(token);
        sessions.assertUsable(decoded);
        if (!accounts.isActive(JwtService.toPrincipal(decoded).getTenantId(), decoded.getSubject())) {
            throw new BadJwtException("Account is not active");
        }
        return decoded;
    }
}
