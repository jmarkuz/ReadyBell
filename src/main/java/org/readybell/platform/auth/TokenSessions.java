package org.readybell.platform.auth;

import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/**
 * Lifecycle of the staff access tokens beyond their expiry:
 * <ul>
 *   <li><b>Sign-out</b> revokes the whole sign-in chain ({@code sid}), so a copied token stops working at once.</li>
 *   <li><b>Refresh rotation</b>: a refreshed token is retired after a short overlap (requests already in flight still
 *       carry it). Presenting a retired token to refresh again means it was copied; the whole chain is revoked, which
 *       signs out both the thief and the owner (the owner just signs in again).</li>
 * </ul>
 */
public class TokenSessions {

    /** How long a refreshed token stays valid, for requests that were already on their way. */
    static final Duration REFRESH_OVERLAP = Duration.ofSeconds(60);

    private final TokenRevocationStore store;
    private final Clock clock;
    private final Duration keepRevoked;

    /** @param keepRevoked how long a revocation must be remembered: the token lifetime plus the refresh grace */
    public TokenSessions(TokenRevocationStore store, Clock clock, Duration keepRevoked) {
        this.store = store;
        this.clock = clock;
        this.keepRevoked = keepRevoked;
    }

    /** The sign-in chain of a token; tokens issued before chains existed are their own chain. */
    public static String sessionIdOf(Jwt jwt) {
        String sid = jwt.getClaimAsString("sid");
        return sid != null && !sid.isBlank() ? sid : jwt.getId();
    }

    public static String newSessionId() {
        return UUID.randomUUID().toString();
    }

    /** @throws BadJwtException if the token (or its chain) was revoked */
    public void assertUsable(Jwt jwt) {
        if (store.isSessionRevoked(sessionIdOf(jwt))) {
            throw new BadJwtException("Session ended");
        }
        if (jwt.getId() != null && store.isTokenRevoked(jwt.getId(), clock.instant())) {
            throw new BadJwtException("Token revoked");
        }
    }

    /** Sign-out: the token and every other token of its chain stop working. */
    public void signOut(Jwt jwt) {
        store.revokeSession(sessionIdOf(jwt), keepRevoked);
    }

    /**
     * Called when {@code old} is presented to refresh.
     * @return false if it must be refused (revoked chain, or a retired token used again, which also ends the chain)
     */
    public boolean allowRefresh(Jwt old) {
        String sid = sessionIdOf(old);
        if (store.isSessionRevoked(sid)) {
            return false;
        }
        if (old.getId() != null && store.isTokenRevoked(old.getId(), clock.instant())) {
            store.revokeSession(sid, keepRevoked);     // a retired token again: it was copied
            return false;
        }
        return true;
    }

    /** The refresh succeeded: retire the old token after the overlap. */
    public void retire(Jwt old) {
        if (old.getId() != null) {
            store.revokeTokenAt(old.getId(), clock.instant().plus(REFRESH_OVERLAP), keepRevoked);
        }
    }
}
