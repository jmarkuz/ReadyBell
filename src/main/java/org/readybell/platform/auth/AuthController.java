package org.readybell.platform.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.readybell.platform.TenantUser;
import org.readybell.platform.ratelimit.ClientIpResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Token endpoints used by the dashboard.
 *
 * <ul>
 *   <li>{@code POST /api/login} — exchanges credentials for a Bearer JWT.</li>
 *   <li>{@code POST /api/auth/refresh} — exchanges a (possibly just-expired) token for a fresh one.
 *       The user is looked up again, so removed users cannot refresh.</li>
 * </ul>
 * Both endpoints are public in {@code SecurityConfig}; failures return 401 problem+json via
 * {@link InvalidCredentialsException}.
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    /** Pre-computed hash compared against when the user is unknown, to equalise timing. */
    private final String dummyHash;

    private final UserDetailsService users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final LoginAttemptLimiter limiter;
    private final ClientIpResolver clientIps;
    private final OperatorNetworkPolicy operatorNetworks;
    private final TokenSessions sessions;

    public AuthController(UserDetailsService users, PasswordEncoder encoder, JwtService jwt,
                          LoginAttemptLimiter limiter, ClientIpResolver clientIps, OperatorNetworkPolicy operatorNetworks,
                          TokenSessions sessions) {
        this.operatorNetworks = operatorNetworks;
        this.sessions = sessions;
        this.limiter = limiter;
        this.clientIps = clientIps;
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        // Behind a reverse proxy the peer is the proxy; the resolver reads the real client from X-Forwarded-For
        String address = clientIps.resolve(http);
        Duration wait = limiter.retryAfter(request.username(), address);
        if (!wait.isZero()) {
            throw new TooManyLoginAttemptsException(wait);
        }
        TenantUser user = lookup(request.username());
        boolean ok = encoder.matches(request.password(), user != null ? user.getPassword() : dummyHash);
        // An operator signing in from outside the allowed networks looks exactly like a wrong password
        if (user == null || !ok || !mayUseFrom(user, address)) {
            limiter.recordFailure(request.username(), address);
            throw new InvalidCredentialsException("Invalid username or password");
        }
        limiter.recordSuccess(request.username(), address);
        return respond(user, TokenSessions.newSessionId());
    }

    /**
     * Ends the sign-in: this token and every token refreshed from it stop working at once, so a copy of the token is
     * useless from now on. Safe to call with an expired or unknown token (nothing to end).
     */
    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return;
        }
        try {
            sessions.signOut(jwt.decodeForRefresh(authorization.substring(7).trim()));
        } catch (JwtException ignored) {
            // not a token of ours (or too old to matter)
        }
    }

    @PostMapping("/auth/refresh")
    public TokenResponse refresh(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                 HttpServletRequest http) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new InvalidCredentialsException("Bearer token required");
        }
        Jwt old;
        try {
            old = jwt.decodeForRefresh(authorization.substring(7).trim());
        } catch (JwtException e) {
            throw new InvalidCredentialsException("Token cannot be refreshed");
        }
        TenantUser user = lookup(old.getSubject());
        if (user == null || !mayUseFrom(user, clientIps.resolve(http)) || !sessions.allowRefresh(old)) {
            throw new InvalidCredentialsException("Token cannot be refreshed");
        }
        TokenResponse refreshed = respond(user, TokenSessions.sessionIdOf(old));
        sessions.retire(old);
        return refreshed;
    }

    private boolean mayUseFrom(TenantUser user, String address) {
        boolean operator = user.getAuthorities().stream().anyMatch(a -> "ROLE_PLATFORM_ADMIN".equals(a.getAuthority()));
        return !operator || operatorNetworks.allows(address);
    }

    private TenantUser lookup(String username) {
        try {
            UserDetails details = users.loadUserByUsername(username);
            // Disabled = deactivated account/restaurant or no password set: indistinguishable from "unknown"
            return details instanceof TenantUser tu && tu.isEnabled() ? tu : null;
        } catch (UsernameNotFoundException e) {
            return null;
        }
    }

    private TokenResponse respond(TenantUser user, String sessionId) {
        JwtService.IssuedToken issued = jwt.issue(user, sessionId);
        return new TokenResponse(issued.token(), issued.role(),
                user.getTenantId() == null ? null : user.getTenantId().value().toString(),
                user.getUsername(), issued.expiresInSeconds());
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record TokenResponse(String token, String role, String tenantId, String username, long expiresIn) {}
}
