package org.readybell.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What must not reach a real deployment: placeholder VAPID keys (push would be rejected without anyone noticing), the
 * development JWT secret or database password, and sign-in users with well-known passwords. Pure functions, so the rules
 * are unit-tested; {@link ProductionStartupChecks} applies them outside the {@code dev} and {@code test} profiles.
 */
public final class StartupChecks {
    private static final Set<String> WEAK_PASSWORDS = Set.of(
            "password", "admin", "platform", "staff", "secret", "changeme", "change-me", "12345678", "qwerty", "test", "root");
    private static final Set<String> KNOWN_DB_PASSWORDS = Set.of("change-me", "changeme", "qwer1234", "password", "postgres", "readybell");

    private StartupChecks() {
    }

    public static List<String> problems(boolean vapidEnabled, String vapidPublicKey, String vapidPrivateKey, String jwtSecret,
                                        String databasePassword, List<SecurityProperties.User> users) {
        var problems = new ArrayList<String>();
        if (vapidEnabled && (isPlaceholder(vapidPublicKey) || isPlaceholder(vapidPrivateKey))) {
            problems.add("VAPID keys are not set, so every Web Push would be rejected without anyone noticing. Generate a pair with "
                    + "scripts/generate-vapid-keys.py and set VAPID_PUBLIC_KEY and VAPID_PRIVATE_KEY (keep them across deploys), "
                    + "or set READYBELL_VAPID_ENABLED=false to run without push.");
        }
        String secret = jwtSecret == null ? "" : jwtSecret.toLowerCase(Locale.ROOT);
        if (secret.contains("dev-only") || secret.contains("change-me") || secret.contains("changeme") || secret.contains("secret-test")) {
            problems.add("JWT_SECRET is a development or example value; set a random secret of at least 32 characters.");
        }
        if (databasePassword != null && KNOWN_DB_PASSWORDS.contains(databasePassword.toLowerCase(Locale.ROOT))) {
            problems.add("DB_PASSWORD is an example or development value; set a real database password.");
        }
        for (var user : users == null ? List.<SecurityProperties.User>of() : users) {
            String password = user.getPassword() == null ? "" : user.getPassword();
            boolean hashed = password.startsWith("{bcrypt}");
            if (!hashed && (WEAK_PASSWORDS.contains(password.toLowerCase(Locale.ROOT))
                    || password.equalsIgnoreCase(user.getUsername()))) {
                problems.add("Configured user '" + user.getUsername() + "' has a well-known password; "
                        + "set a {bcrypt} hash (see .env.example).");
            }
        }
        return problems;
    }

    /** Users whose password is configured as plain text rather than a {bcrypt} hash (allowed, but worth a warning). */
    public static List<String> plainTextPasswordUsers(List<SecurityProperties.User> users) {
        return (users == null ? List.<SecurityProperties.User>of() : users).stream()
                .filter(u -> u.getPassword() != null && !u.getPassword().startsWith("{bcrypt}"))
                .map(SecurityProperties.User::getUsername)
                .toList();
    }

    private static boolean isPlaceholder(String key) {
        return key == null || key.isBlank() || key.startsWith("placeholder-") || key.contains("${");
    }
}
