package org.readybell.session.adapter.in.web;

import org.readybell.session.domain.CustomerSession;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body returned by {@code POST /api/sessions} upon successful session creation.
 *
 * <p>Requirements covered: 1.1, 1.3, 1.6.
 *
 * @param sessionId  the newly created session identifier
 * @param status     the current session status (e.g. "ACTIVE")
 * @param startedAt  the UTC timestamp when the session was started
 */
public record SessionResponse(UUID sessionId, String status, Instant startedAt) {

    /**
     * Constructs a {@code SessionResponse} from the given {@link CustomerSession} aggregate.
     *
     * @param session the newly created session; must not be null
     * @return a response record populated from the session's state
     */
    public static SessionResponse from(CustomerSession session) {
        return new SessionResponse(
                session.getId().value(),
                session.getStatus().name(),
                session.getStartedAt()
        );
    }
}
