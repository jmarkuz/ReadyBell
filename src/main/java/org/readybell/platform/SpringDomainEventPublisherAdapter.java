package org.readybell.platform;

import org.readybell.shared.domain.DomainEvent;
import org.readybell.shared.domain.DomainEventPublisher;
import org.readybell.shared.domain.EventPublicationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Production adapter that implements the {@link DomainEventPublisher} port using
 * Spring's in-process {@link ApplicationEventPublisher} (; ADR-003).
 *
 * <p>Replacing this adapter with a Kafka- or RabbitMQ-backed one satisfies
 * with no modification to any domain or application class.
 *
 * <p>On any publication failure the adapter throws an {@link EventPublicationException}
 * that names the affected event so the caller can record the failure and retain the
 * event.
 */
@Component
public class SpringDomainEventPublisherAdapter implements DomainEventPublisher {
    private final ApplicationEventPublisher applicationEventPublisher;

    public SpringDomainEventPublisherAdapter(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = Objects.requireNonNull(
                applicationEventPublisher, "applicationEventPublisher must not be null");
    }

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
