package org.readybell.ordering.domain;

import java.time.Instant;

/**
 * Outbound port for the order's history: every status change, and one fact row when it ends. Both are written in the
 * transaction of the change itself, so they exist exactly when the change does, and neither holds anything personal.
 * That is what lets the personal data of an old order be removed later without losing the history.
 */
public interface OrderTimelineRepository {

    /** Records a status change; {@code from} is {@code null} when the order is created. */
    void recordTransition(Order order, OrderStatus from, Instant at);

    /** Records the one fact row of a finished (completed or cancelled) order; harmless when repeated. */
    void recordFinished(Order order, Instant at);
}
