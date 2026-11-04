package org.readybell.platform;

import org.readybell.notification.application.OrderReadyHandler;
import org.readybell.ordering.domain.OrderReady;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Spring in-process event listener that bridges Spring's {@link org.springframework.context.ApplicationEventPublisher}
 * to the {@link OrderReadyHandler} application service (Req 11.3; ADR-003).
 *
 * <p>When the {@link SpringDomainEventPublisherAdapter} publishes an {@link OrderReady}
 * event, Spring routes it here via {@link EventListener}, and this listener delegates
 * to the handler. This keeps the domain and application layers free of any Spring
 * annotation or import (Req 11.2).
 */
@Component
public class SpringOrderReadyEventListener {

    private final OrderReadyHandler orderReadyHandler;

    /**
     * @param orderReadyHandler the application-layer handler for order-ready events
     */
    public SpringOrderReadyEventListener(OrderReadyHandler orderReadyHandler) {
        this.orderReadyHandler = Objects.requireNonNull(
                orderReadyHandler, "orderReadyHandler must not be null");
    }

    /**
     * Receives an {@link OrderReady} domain event from Spring's in-process event bus
     * and delegates processing to {@link OrderReadyHandler#handle(OrderReady)}.
     *
     * @param event the order-ready event (must not be {@code null})
     */
    /**
     * Called by the outbox relay on its own thread, so marking an order READY never waits on push services;
     * synchronous on purpose, so a failure reaches the relay and the event is retried.
     */
    @EventListener
    public void onOrderReady(OrderReady event) {
        orderReadyHandler.handle(event);
    }
}
