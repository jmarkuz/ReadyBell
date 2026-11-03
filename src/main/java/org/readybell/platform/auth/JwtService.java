package org.readybell.platform.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.readybell.platform.TenantUser;
import org.readybell.shared.domain.TenantId;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Issues and verifies the HS256 access tokens used by the dashboard.
 *
 * <p>Claims follow the frontend {@code TokenManager} contract: {@code sub} (username),
 * {@code iat}, {@code exp}, {@code role} (primary role, no {@code ROLE_} prefix) and
 * {@code tenant_id}. The full role list is additionally carried in {@code roles}.
 * Platform admins have no tenant, so their {@code tenant_id} is the empty string.
 */
public class JwtService {

    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final JwtDecoder refreshDecoder;
    private final Duration ttl;
    private final Clock clock;

    public JwtService(JwtProperties props, Clock clock) {
        if (props.getSecret() == null || props.getSecret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "readybell.security.jwt.secret must be set and at least 32 bytes long");
        }
        SecretKey key = new SecretKeySpec(props.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.ttl = props.getTtl();
        this.clock = clock;

        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        // Default validators reject expired tokens; the refresh decoder tolerates expiry up to the grace period.
        NimbusJwtDecoder refresh = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        Duration grace = props.getRefreshGrace();
        refresh.setJwtValidator(token -> {
            Instant exp = token.getExpiresAt();
            if (exp == null || clock.instant().isAfter(exp.plus(grace))) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token too old to refresh", null));
            }
            return OAuth2TokenValidatorResult.success();
        });
        this.refreshDecoder = refresh;
    }

    /** Creates a signed access token for the given user. */
    public IssuedToken issue(TenantUser user) {
        Instant now = clock.instant();
        List<String> roles = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .id(UUID.randomUUID().toString())
                .claim("role", roles.isEmpty() ? "" : roles.get(0))
                .claim("roles", roles)
                .claim("tenant_id", user.getTenantId() == null ? "" : user.getTenantId().value().toString())
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new IssuedToken(token, ttl.toSeconds(), roles.isEmpty() ? "" : roles.get(0));
    }

    /** Verifies signature and expiry (with a small clock skew handled by Nimbus defaults). */
    public Jwt decode(String token) {
        return decoder.decode(token);
    }

    /** Verifies the signature and accepts tokens that expired no longer than the refresh grace ago. */
    public Jwt decodeForRefresh(String token) {
        return refreshDecoder.decode(token);
    }

    /** Rebuilds the principal from verified claims (no database lookup per request). */
    public static TenantUser toPrincipal(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null) {
            roles = List.of();
        }
        String tenant = jwt.getClaimAsString("tenant_id");
        TenantId tenantId = (tenant == null || tenant.isBlank()) ? null : new TenantId(UUID.fromString(tenant));
        return new TenantUser(jwt.getSubject(), "",
                roles.stream().<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList(),
                tenantId);
    }

    public record IssuedToken(String token, long expiresInSeconds, String role) {}
}
