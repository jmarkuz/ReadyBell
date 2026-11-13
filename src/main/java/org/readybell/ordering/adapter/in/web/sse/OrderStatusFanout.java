package org.readybell.ordering.adapter.in.web.sse;

import org.readybell.ordering.domain.OrderCreated;
import org.readybell.ordering.domain.OrderStatusUpdated;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
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
        fanOut(new SseMessage(
                event.orderId().value(),
                event.sessionId().value(),
                event.tenantId().value(),
                event.toStatus().name()));
    }

    /**
     * Tells the restaurant's staff panels about a new order (they beep and reload). Runs after the order's transaction
     * has committed, so the reload that follows sees it; without a transaction it runs at once.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderCreated(OrderCreated event) {
        fanOut(SseMessage.created(
                event.orderId().value(),
                event.sessionId().value(),
                event.tenantId().value(),
                event.orderNumber()));
    }

    private void fanOut(SseMessage message) {
        RedisSseBridge bridge = redis.getIfAvailable();
        if (bridge == null || !bridge.publish(message)) {
            registry.deliver(message); // no Redis, or Redis unavailable: at least reach local clients
        }
    }
}
