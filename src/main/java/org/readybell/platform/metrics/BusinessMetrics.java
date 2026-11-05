package org.readybell.platform.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.readybell.ordering.adapter.in.web.sse.SseEmitterRegistry;
import org.readybell.ordering.domain.OrderCreated;
import org.readybell.ordering.domain.OrderStatus;
import org.readybell.ordering.domain.OrderStatusUpdated;
import org.readybell.session.domain.CustomerSessionStarted;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Business meters fed by domain events plus the SSE connection gauges. Tags are enums only; never the tenant
 * or session id (unbounded cardinality).
 */
@Component
public class BusinessMetrics {

    private final Counter sessionsStarted;
    private final Counter ordersCreated;
    private final Map<OrderStatus, Counter> transitions = new EnumMap<>(OrderStatus.class);

    public BusinessMetrics(MeterRegistry registry, SseEmitterRegistry sse) {
        sessionsStarted = Counter.builder("readybell.sessions.started")
                .description("Customer sessions started (QR scanned)").register(registry);
        ordersCreated = Counter.builder("readybell.orders.created")
                .description("Orders created").register(registry);
        for (OrderStatus s : OrderStatus.values()) {
            transitions.put(s, Counter.builder("readybell.orders.transitions").tag("status", s.name())
                    .description("Order status changes by the status entered").register(registry));
        }
        Gauge.builder("readybell.sse.connections", sse, SseEmitterRegistry::staffConnections)
                .tag("audience", "staff").description("Open SSE connections on this node").register(registry);
        Gauge.builder("readybell.sse.connections", sse, SseEmitterRegistry::customerConnections)
                .tag("audience", "customer").description("Open SSE connections on this node").register(registry);
    }

    @EventListener
    public void onSessionStarted(CustomerSessionStarted event) {
        sessionsStarted.increment();
    }

    @EventListener
    public void onOrderCreated(OrderCreated event) {
        ordersCreated.increment();
    }

    @EventListener
    public void onOrderStatusUpdated(OrderStatusUpdated event) {
        transitions.get(event.toStatus()).increment();
    }
}
