package org.readybell.shared.domain;

import java.util.List;

/**
 * Broker-swappable outbound port for publishing domain events.
 *
 * <p>The domain model references ONLY this interface; no concrete messaging
 * implementation (Spring, Kafka, RabbitMQ, etc.) is ever imported from the
 * domain layer.
 *
 * <p>Implementations may publish events in-process (Spring
 * {@code ApplicationEventPublisher}) or delegate to an external broker — the
 * domain is unaffected by that choice.
 */
public interface DomainEventPublisher {
    /**
     * Publishes a single domain event.
     *
     * @throws EventPublicationException if the publication attempt fails
     */
    void publish(DomainEvent event);

    /**
     * Publishes a list of domain events in order.
     *
     * <p>Implementations SHOULD publish all events even when one fails, recording
     * each failure individually so callers can decide how to proceed.
     *
     * @throws EventPublicationException if one or more publication attempts fail
     */
    void publishAll(List<DomainEvent> events);
}
