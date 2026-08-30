package com.example.shared.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Test fake for {@link DomainEventPublisher} that records every published event
 * so tests can assert on what was published.
 *
 * <h2>Default mode</h2>
 * Accepts all events and stores them in publication order. Calling
 * {@link #getPublishedEvents()} returns an unmodifiable snapshot of that list.
 *
 * <h2>Failure mode</h2>
 * When {@link #failForEventTypes(Class[])} is called with one or more event types,
 * the publisher throws {@link EventPublicationException} for any event whose runtime
 * type is in that set. This lets tests verify failure-handling behaviour (Req 11.4).
 *
 * <p>This class is intentionally framework-free (plain Java) and is <em>not</em>
 * annotated with {@code @Component} or any Spring annotation.
 */
public class RecordingDomainEventPublisher implements DomainEventPublisher {

    private final List<DomainEvent> published = new CopyOnWriteArrayList<>();
    private Set<Class<?>> failingTypes = Collections.emptySet();

    // -------------------------------------------------------------------------
    // DomainEventPublisher contract
    // -------------------------------------------------------------------------

    @Override
    public void publish(DomainEvent event) {
        if (shouldFail(event)) {
            throw EventPublicationException.forEvent(
                    event,
                    "Simulated publication failure for event type: " + event.getClass().getSimpleName()
            );
        }
        published.add(event);
    }

    @Override
    public void publishAll(List<DomainEvent> events) {
        List<EventPublicationException> failures = new ArrayList<>();

        for (DomainEvent event : events) {
            if (shouldFail(event)) {
                failures.add(EventPublicationException.forEvent(
                        event,
                        "Simulated publication failure for event type: " + event.getClass().getSimpleName()
                ));
            } else {
                published.add(event);
            }
        }

        if (!failures.isEmpty()) {
            // Surface the first failure; callers can interrogate getPublishedEvents()
            // for the events that did succeed.
            throw failures.get(0);
        }
    }

    // -------------------------------------------------------------------------
    // Inspection helpers
    // -------------------------------------------------------------------------

    /**
     * Returns an unmodifiable snapshot of all events that were successfully published
     * (i.e. those that did not throw).
     */
    public List<DomainEvent> getPublishedEvents() {
        return Collections.unmodifiableList(new ArrayList<>(published));
    }

    /**
     * Returns all successfully published events whose runtime type is exactly
     * {@code eventClass} (or a subtype of it).
     *
     * @param <T>        the event type to filter on
     * @param eventClass the class to filter by
     * @return a list of matching events, possibly empty
     */
    @SuppressWarnings("unchecked")
    public <T extends DomainEvent> List<T> publishedEventsOfType(Class<T> eventClass) {
        return published.stream()
                .filter(eventClass::isInstance)
                .map(e -> (T) e)
                .collect(Collectors.toList());
    }

    /**
     * Returns the total number of events successfully published.
     */
    public int publishedCount() {
        return published.size();
    }

    /**
     * Returns {@code true} if no events have been successfully published.
     */
    public boolean hasNoPublishedEvents() {
        return published.isEmpty();
    }

    // -------------------------------------------------------------------------
    // Configuration helpers
    // -------------------------------------------------------------------------

    /**
     * Configures the fake to throw {@link EventPublicationException} for any event
     * whose runtime type is one of the supplied types.
     *
     * <p>Call {@link #acceptAll()} to revert to the default accept-all behaviour.
     *
     * @param eventTypes event classes that should trigger a failure
     */
    @SafeVarargs
    public final void failForEventTypes(Class<? extends DomainEvent>... eventTypes) {
        this.failingTypes = Set.of(eventTypes);
    }

    /**
     * Reverts the fake to the default mode where all events are accepted.
     */
    public void acceptAll() {
        this.failingTypes = Collections.emptySet();
    }

    /**
     * Clears all recorded events and resets failure configuration.
     * Useful when reusing a single fake across multiple test cases.
     */
    public void reset() {
        published.clear();
        failingTypes = Collections.emptySet();
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private boolean shouldFail(DomainEvent event) {
        return failingTypes.stream().anyMatch(type -> type.isInstance(event));
    }
}
