package org.readybell.ordering.application;

import org.readybell.ordering.domain.Order;

import java.util.List;

/**
 * What a customer may see of their session: the restaurant, whether the session is still open and
 * its orders, oldest first.
 */
public record SessionTracking(String restaurantName, boolean sessionActive, List<Order> orders) {
}
