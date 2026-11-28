package org.readybell.shared.domain;

import java.util.UUID;

/**
 * Unchecked exception thrown when the {@link DomainEventPublisher} port fails to
 * accept a {@link DomainEvent} for publication.
 *
 * <p>The exception carries enough information to identify the affected event
 * ({@code eventType} and {@code eventId}) so that callers can record the failure
 * and retain the event without discarding it.
 */
public class EventPublicationException extends RuntimeException {
    private final String eventType;
    private final UUID eventId;

    public EventPublicationException(String eventType, UUID eventId, String message) {
        super(message);
        this.eventType = eventType;
        this.eventId = eventId;
    }

    public EventPublicationException(String eventType, UUID eventId, String message, Throwable cause) {
        super(message, cause);
        this.eventType = eventType;
        this.eventId = eventId;
    }

    /**
     * Convenience factory that derives {@code eventType} and {@code eventId} directly
     * from the failing event.
     */
    public static EventPublicationException forEvent(DomainEvent event, String message) {
        return new EventPublicationException(
                event.getClass().getSimpleName(),
                event.eventId(),
                message
        );
    }

    /**
     * Convenience factory that derives {@code eventType} and {@code eventId} from the
     * failing event and includes an underlying cause.
     */
    public static EventPublicationException forEvent(DomainEvent event, String message, Throwable cause) {
        return new EventPublicationException(
                event.getClass().getSimpleName(),
                event.eventId(),
                message,
                cause
        );
    }

    public String getEventType() {
        return eventType;
    }

    public UUID getEventId() {
        return eventId;
    }
}
