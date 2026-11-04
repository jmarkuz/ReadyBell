package org.readybell.ordering.application;

import org.readybell.ordering.domain.Order;
import org.readybell.ordering.domain.OrderRepository;
import org.readybell.restaurant.domain.RestaurantRepository;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.session.domain.CustomerSessionRepository;
import org.readybell.shared.domain.TenantId;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/** {@link TrackSessionUseCase} over the session, order and restaurant ports. */
public class TrackSessionService implements TrackSessionUseCase {

    private final CustomerSessionRepository sessions;
    private final OrderRepository orders;
    private final RestaurantRepository restaurants;

    public TrackSessionService(CustomerSessionRepository sessions, OrderRepository orders,
                               RestaurantRepository restaurants) {
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
        this.orders = Objects.requireNonNull(orders, "orders must not be null");
        this.restaurants = Objects.requireNonNull(restaurants, "restaurants must not be null");
    }

    @Override
    public Optional<SessionTracking> track(TenantId tenantId, CustomerSessionId sessionId) {
        return sessions.findByTenantAndId(tenantId, sessionId).map(session -> new SessionTracking(
                restaurants.findByTenantId(tenantId).map(r -> r.getConfig().name()).orElse(""),
                session.isActive(),
                orders.findByTenantAndSession(tenantId, sessionId).stream()
                        .sorted(Comparator.comparing(Order::getCreatedAt))
                        .toList()));
    }
}
