package com.example.platform;

import com.example.shared.domain.DomainEvent;
import com.example.shared.domain.DomainEventPublisher;
import com.example.shared.domain.EventPublicationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Production adapter that implements the {@link DomainEventPublisher} port using
 * Spring's in-process {@link ApplicationEventPublisher} (Req 11.1, 11.3; ADR-003).
 *
 * <p>Replacing this adapter with a Kafka- or RabbitMQ-backed one satisfies Req 11.5
 * with no modification to any domain or application class.
 *
 * <p>On any publication failure the adapter throws an {@link EventPublicationException}
 * that names the affected event so the caller can record the failure and retain the
 * event (Req 11.4).
 */
@Component
public class SpringDomainEventPublisherAdapter implements DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * @param applicationEventPublisher Spring's event publisher, injected by the container
     */
    public SpringDomainEventPublisherAdapter(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = Objects.requireNonNull(
                applicationEventPublisher, "applicationEventPublisher must not be null");
    }

    /**
     * Publishes a single domain event through Spring's in-process event bus.
     *
     * @param event the event to publish; must not be {@code null}
     * @throws EventPublicationException if Spring's publisher throws any exception (Req 11.4)
     */
    @Override
    public void publish(DomainEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        try {
            applicationEventPublisher.publishEvent(event);
        } catch (Exception ex) {
            throw EventPublicationException.forEvent(
                    event,
                    "Failed to publish event: " + ex.getMessage(),
                    ex);
        }
    }

    /**
     * Publishes all domain events in order.
     *
     * <p>All events are attempted even when one fails; failures are collected and, if
     * any exist, the first failure is re-thrown after the full list has been processed
     * (Req 11.4).
     *
     * @param events the events to publish; must not be {@code null}
     * @throws EventPublicationException if one or more publication attempts fail (Req 11.4)
     */
    @Override
    public void publishAll(List<DomainEvent> events) {
        Objects.requireNonNull(events, "events must not be null");

        List<EventPublicationException> failures = new ArrayList<>();

        for (DomainEvent event : events) {
            try {
                applicationEventPublisher.publishEvent(event);
            } catch (Exception ex) {
                failures.add(EventPublicationException.forEvent(
                        event,
                        "Failed to publish event: " + ex.getMessage(),
                        ex));
            }
        }

        if (!failures.isEmpty()) {
            throw failures.get(0);
        }
    }
}
