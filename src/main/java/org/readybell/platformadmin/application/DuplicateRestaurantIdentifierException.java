package org.readybell.platformadmin.application;

/**
 * Thrown when a Platform Admin attempts to create a Restaurant with a business
 * identifier that is already in use by another tenant.
 */
public class DuplicateRestaurantIdentifierException extends RuntimeException {
    private final String identifier;

    public DuplicateRestaurantIdentifierException(String identifier) {
        super("A restaurant with identifier '" + identifier + "' already exists");
        this.identifier = identifier;
    }

    public String getIdentifier() {
        return identifier;
    }
}
