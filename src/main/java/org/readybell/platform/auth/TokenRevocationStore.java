package org.readybell.platform.auth;

import java.time.Duration;
import java.time.Instant;

/**
 * Which access tokens must no longer be accepted. A token carries a session id ({@code sid}, shared by every token of one
 * sign-in chain) and its own id ({@code jti}); either can be revoked. Entries only need to live as long as a token could
 * still be used, so they expire on their own.
 */
public interface TokenRevocationStore {

    /** Invalidates every token of the sign-in chain (sign-out, or a refresh token that was used twice). */
    void revokeSession(String sessionId, Duration keepFor);

    boolean isSessionRevoked(String sessionId);

    /** Invalidates one token from {@code at} on (a refreshed token stays valid for a short overlap for requests in flight). */
    void revokeTokenAt(String tokenId, Instant at, Duration keepFor);

    boolean isTokenRevoked(String tokenId, Instant now);
}
