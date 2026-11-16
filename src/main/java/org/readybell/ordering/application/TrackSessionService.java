package org.readybell.ordering.application;

import org.readybell.ordering.domain.Order;
import org.readybell.ordering.domain.OrderConnected;
import org.readybell.ordering.domain.OrderRepository;
import org.readybell.restaurant.domain.RestaurantRepository;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.session.domain.CustomerSessionRepository;
import org.readybell.shared.domain.DomainEventPublisher;
import org.readybell.shared.domain.TenantId;

import java.time.Clock;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link TrackSessionUseCase} over the session, order and restaurant ports.
 *
 * <p>The first time a customer opens an order's tracking link, the order is marked connected and an
 * {@link OrderConnected} event tells the staff screens (they show a tick and hide the QR code). Only unfinished orders
 * count: opening the link of an old, completed order must not signal anything at the counter.
 */
public class TrackSessionService implements TrackSessionUseCase {

    private final CustomerSessionRepository sessions;
    private final OrderRepository orders;
    private final RestaurantRepository restaurants;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public TrackSessionService(CustomerSessionRepository sessions, OrderRepository orders,
                               RestaurantRepository restaurants, DomainEventPublisher eventPublisher, Clock clock) {
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
        this.orders = Objects.requireNonNull(orders, "orders must not be null");
        this.restaurants = Objects.requireNonNull(restaurants, "restaurants must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Optional<SessionTracking> track(TenantId tenantId, CustomerSessionId sessionId) {
        return sessions.findByTenantAndId(tenantId, sessionId).map(session -> {
            var sessionOrders = orders.findByTenantAndSession(tenantId, sessionId);
            for (Order order : sessionOrders) {
                if (!order.getStatus().isTerminal() && order.markConnected(clock)) {
                    orders.save(order);
                    eventPublisher.publish(OrderConnected.of(order.getId(), tenantId, sessionId, clock.instant(),
                            order.getOrderNumber()));
                }
            }
            return new SessionTracking(
                    restaurants.findByTenantId(tenantId).map(r -> r.getConfig().name()).orElse(""),
                    session.isActive(),
                    sessionOrders.stream().sorted(Comparator.comparing(Order::getCreatedAt)).toList());
        });
    }
}
