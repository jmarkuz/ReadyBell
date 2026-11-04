package org.readybell.session.adapter.in.web;

import org.readybell.ordering.domain.Order;
import org.readybell.ordering.domain.OrderItem;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * What the customer sees on the tracking page opened from the receipt QR code: the restaurant and
 * every order of the session. Deliberately contains no tenant, customer or staff identifiers.
 */
public record TrackResponse(String restaurantName, boolean sessionActive, List<TrackedOrder> orders) {

    public record TrackedOrder(String orderNumber, String status, Instant estimatedReadyAt,
                               Long estimatedWaitMinutes, List<Item> items, Instant updatedAt) {

        static TrackedOrder from(Order order, Clock clock) {
            Instant eta = order.getEstimatedReadyAt();
            Long wait = eta == null ? null : Math.max(0, Duration.between(clock.instant(), eta).toMinutes());
            return new TrackedOrder(
                    "#" + order.getId().value().toString().substring(0, 8).toUpperCase(Locale.ROOT),
                    order.getStatus().name(), eta, wait,
                    order.getItems().stream().map(Item::from).toList(),
                    order.getUpdatedAt());
        }
    }

    public record Item(String name, int quantity) {
        static Item from(OrderItem item) {
            return new Item(item.name(), item.quantity());
        }
    }
}
