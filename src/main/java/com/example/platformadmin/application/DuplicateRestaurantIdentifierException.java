package com.example.platformadmin.application;

/**
 * Thrown when a Platform Admin attempts to create a Restaurant with a business
 * identifier that is already in use by another tenant (Req 9.5).
 */
public class DuplicateRestaurantIdentifierException extends RuntimeException {

    private final String identifier;

    public DuplicateRestaurantIdentifierException(String identifier) {
        super("A restaurant with identifier '" + identifier + "' already exists");
        this.identifier = identifier;
    }

    /** The duplicate business identifier that triggered this exception. */
    public String getIdentifier() {
        return identifier;
    }
}
