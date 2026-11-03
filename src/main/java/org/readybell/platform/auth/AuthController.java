package org.readybell.platform.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.readybell.platform.TenantUser;
import org.springframework.http.HttpHeaders;

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

    public AuthController(UserDetailsService users, PasswordEncoder encoder, JwtService jwt,
                          LoginAttemptLimiter limiter) {
        this.limiter = limiter;
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String address = http.getRemoteAddr();
        Duration wait = limiter.retryAfter(request.username(), address);
        if (!wait.isZero()) {
            throw new TooManyLoginAttemptsException(wait);
        }
        TenantUser user = lookup(request.username());
        boolean ok = encoder.matches(request.password(), user != null ? user.getPassword() : dummyHash);
        if (user == null || !ok) {
            limiter.recordFailure(request.username(), address);
            throw new InvalidCredentialsException("Invalid username or password");
        }
        limiter.recordSuccess(request.username());
        return respond(user);
    }

    @PostMapping("/auth/refresh")
    public TokenResponse refresh(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
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
        if (user == null) {
            throw new InvalidCredentialsException("Token cannot be refreshed");
        }
        return respond(user);
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

    private TokenResponse respond(TenantUser user) {
        JwtService.IssuedToken issued = jwt.issue(user);
        return new TokenResponse(issued.token(), issued.role(),
                user.getTenantId() == null ? null : user.getTenantId().value().toString(),
                user.getUsername(), issued.expiresInSeconds());
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record TokenResponse(String token, String role, String tenantId, String username, long expiresIn) {}
}
