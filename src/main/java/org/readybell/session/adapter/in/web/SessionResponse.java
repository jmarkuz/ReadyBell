package org.readybell.session.adapter.in.web;

import org.readybell.session.domain.CustomerSession;

import java.time.Instant;
import java.util.UUID;

/** Response body returned by {@code POST /api/sessions} upon successful session creation. */
public record SessionResponse(UUID sessionId, String status, Instant startedAt) {
    /** Constructs a {@code SessionResponse} from the given {@link CustomerSession} aggregate. */
    public static SessionResponse from(CustomerSession session) {
        return new SessionResponse(
                session.getId().value(),
                session.getStatus().name(),
                session.getStartedAt()
        );
    }
}
