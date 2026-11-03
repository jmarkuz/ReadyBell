package org.readybell.restaurant.application;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Rules for staff usernames. Sign-in takes only a username, so names are compared
 * case-insensitively and must be unique across the whole platform; they are stored normalised
 * (stripped, lower-case).
 */
public final class UsernamePolicy {

    private static final Pattern ALLOWED = Pattern.compile("[a-z0-9][a-z0-9._@-]{2,63}");

    private UsernamePolicy() {}

    /**
     * @return the normalised username
     * @throws IllegalArgumentException if it is null or not 3-64 characters of {@code a-z 0-9 . _ @ -}
     *                                  (starting with a letter or digit) after normalisation
     */
    public static String normalize(String username) {
        if (username == null) {
            throw new IllegalArgumentException("username must not be null");
        }
        String normalized = username.strip().toLowerCase(Locale.ROOT);
        if (!ALLOWED.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "username must be 3-64 characters: letters, digits, '.', '_', '@' or '-'");
        }
        return normalized;
    }
}
