package org.readybell.ordering.adapter.in.web;

import org.readybell.ordering.application.SessionTracking;
import org.readybell.ordering.domain.Order;
import org.readybell.ordering.domain.OrderItem;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * What the customer sees on the tracking page opened from the receipt QR code: the restaurant and
 * every order of the session. Deliberately contains no tenant, customer or staff identifiers.
 */
public record TrackResponse(String restaurantName, boolean sessionActive, List<TrackedOrder> orders) {

    static TrackResponse from(SessionTracking tracking, Clock clock) {
        return new TrackResponse(tracking.restaurantName(), tracking.sessionActive(),
                tracking.orders().stream().map(o -> TrackedOrder.from(o, clock)).toList());
    }

    public record TrackedOrder(String orderNumber, String status, Instant estimatedReadyAt,
                               Long estimatedWaitMinutes, List<Item> items, Instant updatedAt) {

        static TrackedOrder from(Order order, Clock clock) {
            Instant eta = order.getEstimatedReadyAt();
            Long wait = eta == null ? null : Math.max(0, Duration.between(clock.instant(), eta).toMinutes());
            return new TrackedOrder(
                    OrderRowResponse.displayNumber(order),
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
