package org.readybell.ordering.adapter.in.web.sse;

import org.readybell.ordering.domain.OrderStatusUpdated;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns {@link OrderStatusUpdated} events into SSE pushes.
 *
 * <p>By default the push goes straight to this node's connections. With
 * {@code readybell.sse.redis.enabled=true} it is published through Redis instead, so that every
 * node — including the one that handled the request — delivers to its own connections and clients
 * receive updates regardless of which replica they are attached to (Req 12.5).
 */
@Component
public class OrderStatusFanout {

    private final SseEmitterRegistry registry;
    private final ObjectProvider<RedisSseBridge> redis;

    public OrderStatusFanout(SseEmitterRegistry registry, ObjectProvider<RedisSseBridge> redis) {
        this.registry = registry;
        this.redis = redis;
    }

    @EventListener
    public void onOrderStatusUpdated(OrderStatusUpdated event) {
        SseMessage message = new SseMessage(
                event.orderId().value(),
                event.sessionId().value(),
                event.tenantId().value(),
                event.toStatus().name());
        RedisSseBridge bridge = redis.getIfAvailable();
        if (bridge == null || !bridge.publish(message)) {
            registry.deliver(message); // no Redis, or Redis unavailable: at least reach local clients
        }
    }
}
