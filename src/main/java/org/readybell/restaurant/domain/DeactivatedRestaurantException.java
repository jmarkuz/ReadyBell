package org.readybell.restaurant.domain;

/**
 * Thrown when an operation that requires an active Restaurant is attempted
 * against a deactivated one — e.g. starting a new customer session.
 */
public class DeactivatedRestaurantException extends RuntimeException {
    private final String restaurantIdentifier;

    public DeactivatedRestaurantException(String restaurantIdentifier) {
        super("Restaurant '" + restaurantIdentifier + "' is deactivated and cannot accept new sessions");
        this.restaurantIdentifier = restaurantIdentifier;
    }

    public String getRestaurantIdentifier() {
        return restaurantIdentifier;
    }
}
