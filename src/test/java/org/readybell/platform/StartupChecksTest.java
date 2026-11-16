package org.readybell.platform;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StartupChecksTest {

    private static final String KEY = "BNem4e8WUGXqteeTJNwrrS5D4fIytCUELHDh7fD8zGNy5qgC0tlw7ABC";
    private static final String SECRET = "a-long-random-secret-0123456789-xyz";

    private static SecurityProperties.User user(String name, String password) {
        var u = new SecurityProperties.User();
        u.setUsername(name);
        u.setPassword(password);
        return u;
    }

    private static List<String> check(boolean vapidEnabled, String pub, String priv, String secret, String db, SecurityProperties.User... users) {
        return StartupChecks.problems(vapidEnabled, pub, priv, secret, db, List.of(users));
    }

    @Test
    void aRealConfigurationHasNoProblems() {
        assertThat(check(true, KEY, KEY, SECRET, "s0me-real-db-pass", user("platform", "{bcrypt}$2a$10$abc"))).isEmpty();
    }

    @Test
    void placeholderOrMissingVapidKeysAreCaughtUnlessPushIsDisabled() {
        assertThat(check(true, "placeholder-public-key", "placeholder-private-key", SECRET, "x")).hasSize(1)
                .allMatch(p -> p.contains("VAPID") && p.contains("READYBELL_VAPID_ENABLED=false"));
        assertThat(check(true, "", KEY, SECRET, "x")).hasSize(1);
        assertThat(check(true, "${VAPID_PUBLIC_KEY:placeholder-public-key}", KEY, SECRET, "x")).hasSize(1);
        assertThat(check(false, "placeholder-public-key", "placeholder-private-key", SECRET, "x")).isEmpty();
    }

    @Test
    void developmentSecretsAreCaught() {
        assertThat(check(true, KEY, KEY, "dev-only-secret-change-me-0123456789abcdef", "x")).anyMatch(p -> p.contains("JWT_SECRET"));
        assertThat(check(true, KEY, KEY, SECRET, "change-me")).anyMatch(p -> p.contains("DB_PASSWORD"));
        assertThat(check(true, KEY, KEY, SECRET, "qwer1234")).anyMatch(p -> p.contains("DB_PASSWORD"));
    }

    @Test
    void usersWithWellKnownPlainPasswordsAreCaughtButAHashIsNot() {
        assertThat(check(true, KEY, KEY, SECRET, "x", user("admin", "admin"), user("platform", "Platform"))).hasSize(2);
        assertThat(check(true, KEY, KEY, SECRET, "x", user("ops", "ops"))).hasSize(1);                       // same as the username
        assertThat(check(true, KEY, KEY, SECRET, "x", user("admin", "{bcrypt}$2a$10$abc"))).isEmpty();
        assertThat(check(true, KEY, KEY, SECRET, "x", user("ops", "a-long-unique-passphrase"))).isEmpty();   // plain but not weak
    }

    @Test
    void plainTextPasswordsAreListedForAWarning() {
        assertThat(StartupChecks.plainTextPasswordUsers(List.of(user("a", "x-long-passphrase"), user("b", "{bcrypt}$2a$10$abc"))))
                .containsExactly("a");
    }

    @Test
    void everyProblemIsReportedAtOnce() {
        assertThat(check(true, "placeholder-public-key", "placeholder-private-key", "changeme-secret-0123456789abcdef-xyz", "change-me",
                user("admin", "admin"))).hasSize(4);
    }
}
