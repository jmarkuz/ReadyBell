package org.readybell.shared.domain;

import java.util.UUID;

/**
 * Unchecked exception thrown when the {@link DomainEventPublisher} port fails to
 * accept a {@link DomainEvent} for publication (Req 11.4).
 *
 * <p>The exception carries enough information to identify the affected event
 * ({@code eventType} and {@code eventId}) so that callers can record the failure
 * and retain the event without discarding it.
 */
public class EventPublicationException extends RuntimeException {

    private final String eventType;
    private final UUID eventId;

    /**
     * Creates an {@code EventPublicationException} for the given event.
     *
     * @param eventType simple class name (or logical type name) of the failed event
     * @param eventId   the unique identifier of the failed event
     * @param message   human-readable description of the failure
     */
    public EventPublicationException(String eventType, UUID eventId, String message) {
        super(message);
        this.eventType = eventType;
        this.eventId = eventId;
    }

    /**
     * Creates an {@code EventPublicationException} with an underlying cause.
     *
     * @param eventType simple class name (or logical type name) of the failed event
     * @param eventId   the unique identifier of the failed event
     * @param message   human-readable description of the failure
     * @param cause     the root cause of the publication failure
     */
    public EventPublicationException(String eventType, UUID eventId, String message, Throwable cause) {
        super(message, cause);
        this.eventType = eventType;
        this.eventId = eventId;
    }

    /**
     * Convenience factory that derives {@code eventType} and {@code eventId} directly
     * from the failing event.
     *
     * @param event   the event that could not be published
     * @param message human-readable description of the failure
     * @return a new {@code EventPublicationException}
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
     *
     * @param event   the event that could not be published
     * @param message human-readable description of the failure
     * @param cause   the root cause
     * @return a new {@code EventPublicationException}
     */
    public static EventPublicationException forEvent(DomainEvent event, String message, Throwable cause) {
        return new EventPublicationException(
                event.getClass().getSimpleName(),
                event.eventId(),
                message,
                cause
        );
    }

    /** @return the simple class name (or logical type name) of the affected event. */
    public String getEventType() {
        return eventType;
    }

    /** @return the unique identifier of the affected event. */
    public UUID getEventId() {
        return eventId;
    }
}
