package org.readybell.platform.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT settings ({@code readybell.security.jwt.*}).
 *
 * <p>{@code secret} is the HS256 signing key (at least 32 bytes) and has no default: each
 * environment must provide it, otherwise the application fails fast at startup.
 */
@ConfigurationProperties(prefix = "readybell.security.jwt")
public class JwtProperties {

    private String secret;
    private Duration ttl = Duration.ofHours(1);
    /** How long after expiry an old token may still be exchanged at {@code /api/auth/refresh}. */
    private Duration refreshGrace = Duration.ofDays(7);

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public Duration getTtl() { return ttl; }
    public void setTtl(Duration ttl) { this.ttl = ttl; }
    public Duration getRefreshGrace() { return refreshGrace; }
    public void setRefreshGrace(Duration refreshGrace) { this.refreshGrace = refreshGrace; }
}
