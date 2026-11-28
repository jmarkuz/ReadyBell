package org.readybell.subscription.domain;

/**
 * Value object representing the three pieces of data required to deliver a
 * Web Push message to a specific customer device.
 *
 * <p>All three fields are mandatory; a subscription that is missing any of
 * them cannot receive messages and is therefore invalid.
 */
public record PushEndpoint(String endpointUrl, String p256dhKey, String authKey) {
    /**
     * Compact constructor that validates every field.
     *
     * @throws IllegalArgumentException if any field is {@code null} or blank
     */
    public PushEndpoint {
        requireNonBlank(endpointUrl, "endpointUrl");
        requireNonBlank(p256dhKey, "p256dhKey");
        requireNonBlank(authKey, "authKey");
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "PushEndpoint field '" + fieldName + "' must not be null or blank");
        }
    }
}
