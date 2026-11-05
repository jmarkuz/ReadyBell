package org.readybell.ordering.adapter.in.web;

import org.readybell.ordering.domain.Order;
import org.readybell.ordering.domain.OrderItem;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * One row of the staff order table (the dashboard's {@code OrderRow}).
 *
 * @param id                 order UUID
 * @param orderId            short display number, e.g. {@code #101}
 * @param customerName       may be {@code null}
 * @param itemsOrdered       e.g. {@code "2x Burger, Fries"}; empty string when no items
 * @param deliveryETA        expected ready time; may be {@code null}
 * @param estimatedWaitTime  whole minutes until the ETA, never negative; {@code null} without ETA
 * @param itemCount          total quantity over all items
 */
public record OrderRowResponse(UUID id, String orderId, String customerName, String itemsOrdered,
                               Instant deliveryETA, String status, Long estimatedWaitTime, int itemCount,
                               Instant createdAt, Instant updatedAt) {

    static OrderRowResponse from(Order order, Clock clock) {
        Instant eta = order.getEstimatedReadyAt();
        Long wait = eta == null ? null : Math.max(0, Duration.between(clock.instant(), eta).toMinutes());
        return new OrderRowResponse(
                order.getId().value(),
                displayNumber(order),
                order.getCustomerName(),
                order.getItems().stream()
                        .map(i -> i.quantity() > 1 ? i.quantity() + "x " + i.name() : i.name())
                        .collect(Collectors.joining(", ")),
                eta,
                order.getStatus().name(),
                wait,
                order.getItems().stream().mapToInt(OrderItem::quantity).sum(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    /** {@code #} + the restaurant's sequential order number, e.g. {@code #101}. */
    static String displayNumber(Order order) {
        return "#" + order.getOrderNumber();
    }
}
