package org.readybell.platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Refuses to start a real deployment with development or placeholder settings (see {@link StartupChecks}). The
 * {@code dev} and {@code test} profiles are exempt; they are for a developer's machine.
 */
@Component
@Profile("!dev & !test")
class ProductionStartupChecks {
    private static final Logger log = LoggerFactory.getLogger(ProductionStartupChecks.class);

    ProductionStartupChecks(
            @Value("${readybell.vapid.enabled:true}") boolean vapidEnabled,
            @Value("${readybell.vapid.public-key:}") String vapidPublicKey,
            @Value("${readybell.vapid.private-key:}") String vapidPrivateKey,
            @Value("${readybell.security.jwt.secret:}") String jwtSecret,
            @Value("${spring.datasource.password:}") String databasePassword,
            SecurityProperties security) {
        List<String> problems = StartupChecks.problems(vapidEnabled, vapidPublicKey, vapidPrivateKey, jwtSecret,
                databasePassword, security.getUsers());
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Refusing to start with an unsafe configuration:\n - " + String.join("\n - ", problems));
        }
        List<String> plain = StartupChecks.plainTextPasswordUsers(security.getUsers());
        if (!plain.isEmpty()) {
            log.warn("Configured users with a plain-text password (prefer a {bcrypt} hash, see .env.example): {}", plain);
        }
    }
}
