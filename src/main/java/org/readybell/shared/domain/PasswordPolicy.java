package org.readybell.shared.domain;

import java.util.Locale;
import java.util.Set;

/**
 * Rules for passwords of the accounts people type in (restaurant admins and staff). Length and obvious guessability are
 * what matter, not composition rules: at least {@value #MIN_LENGTH} characters, at most {@value #MAX_LENGTH} (bcrypt reads
 * no further and would silently ignore the rest), not a well-known password, not the username, and not one repeated
 * character or a plain run like {@code 1234567890}.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 72;

    /** The passwords attackers try first, plus this product's own name. All compared in lower case. */
    private static final Set<String> COMMON = Set.of(
            "password", "password1", "password12", "password123", "password1234", "passw0rd123", "p@ssw0rd123",
            "1234567890", "12345678910", "123456789012", "0123456789", "0987654321", "1q2w3e4r5t", "1qaz2wsx3edc",
            "qwertyuiop", "qwerty1234", "qwerty12345", "qwerty123456", "asdfghjkl1", "asdfghjkl123", "zxcvbnm123",
            "iloveyou12", "iloveyou123", "welcome123", "welcome1234", "letmein123", "letmein1234", "changeme123",
            "change-me", "change-me-now", "admin12345", "admin123456", "administrator", "adminadmin", "superadmin",
            "readybell", "readybell1", "readybell123", "readybell1234", "restaurant", "restaurant1", "restaurant123",
            "cafe123456", "test123456", "testtest12", "default123", "secret1234", "s3cret1234", "monkey1234");

    private PasswordPolicy() {
    }

    /** @return why the password is not acceptable, or {@code null} if it is */
    public static String violation(String username, String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return "Password must be at least " + MIN_LENGTH + " characters";
        }
        if (password.length() > MAX_LENGTH) {
            return "Password must be at most " + MAX_LENGTH + " characters";
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower)) {
            return "That password is too common; choose another";
        }
        if (username != null && username.strip().length() >= 3 && lower.contains(username.strip().toLowerCase(Locale.ROOT))) {
            return "Password must not contain the username";
        }
        if (lower.chars().distinct().count() < 5 || isRun(lower)) {
            return "Password is too predictable; avoid repeated characters and simple sequences";
        }
        return null;
    }

    /** True for a plain ascending or descending run of characters such as {@code abcdefghij} or {@code 9876543210}. */
    private static boolean isRun(String s) {
        boolean up = true;
        boolean down = true;
        for (int i = 1; i < s.length(); i++) {
            int step = s.charAt(i) - s.charAt(i - 1);
            up &= step == 1;
            down &= step == -1;
        }
        return up || down;
    }
}
