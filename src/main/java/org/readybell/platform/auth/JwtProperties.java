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
    private Duration refreshGrace = Duration.ofHours(24);

    private final Login login = new Login();

    public Login getLogin() { return login; }

    public static class Login {
        private int maxFailures = 5;
        private int maxFailuresPerAddress = 30;
        /** Failed logins for one account (any addresses) before the account is locked for everyone; deliberately high. */
        private int maxFailuresPerAccount = 100;
        private Duration window = Duration.ofMinutes(15);
        private boolean redis = false;

        public int getMaxFailuresPerAddress() { return maxFailuresPerAddress; }
        public void setMaxFailuresPerAddress(int v) { this.maxFailuresPerAddress = v; }
        public int getMaxFailuresPerAccount() { return maxFailuresPerAccount; }
        public void setMaxFailuresPerAccount(int v) { this.maxFailuresPerAccount = v; }
        public int getMaxFailures() { return maxFailures; }
        public void setMaxFailures(int maxFailures) { this.maxFailures = maxFailures; }
        public Duration getWindow() { return window; }
        public void setWindow(Duration window) { this.window = window; }
        public boolean isRedis() { return redis; }
        public void setRedis(boolean redis) { this.redis = redis; }
    }

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public Duration getTtl() { return ttl; }
    public void setTtl(Duration ttl) { this.ttl = ttl; }
    public Duration getRefreshGrace() { return refreshGrace; }
    public void setRefreshGrace(Duration refreshGrace) { this.refreshGrace = refreshGrace; }
}
