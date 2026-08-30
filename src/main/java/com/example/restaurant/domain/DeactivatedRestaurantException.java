package com.example.restaurant.domain;

/**
 * Thrown when an operation that requires an active Restaurant is attempted
 * against a deactivated one — e.g. starting a new customer session (Req 9.6).
 */
public class DeactivatedRestaurantException extends RuntimeException {

    private final String restaurantIdentifier;

    public DeactivatedRestaurantException(String restaurantIdentifier) {
        super("Restaurant '" + restaurantIdentifier + "' is deactivated and cannot accept new sessions");
        this.restaurantIdentifier = restaurantIdentifier;
    }

    /** The business identifier of the deactivated restaurant. */
    public String getRestaurantIdentifier() {
        return restaurantIdentifier;
    }
}
