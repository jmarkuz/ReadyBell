package org.readybell.notification.domain;

/**
 * Value object carrying the content of a Web Push notification.
 *
 * <p>Both {@code title} and {@code body} are required; a payload missing either
 * field cannot produce a meaningful customer notification.
 *
 * @param title the notification title shown in the browser/OS notification center
 * @param body  the notification body text
 */
public record NotificationPayload(String title, String body) {

    /**
     * Compact constructor that validates both fields.
     *
     * @throws IllegalArgumentException if {@code title} or {@code body} is {@code null}
     *                                  or blank
     */
    public NotificationPayload {
        requireNonBlank(title, "title");
        requireNonBlank(body, "body");
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "NotificationPayload field '" + fieldName + "' must not be null or blank");
        }
    }
}
