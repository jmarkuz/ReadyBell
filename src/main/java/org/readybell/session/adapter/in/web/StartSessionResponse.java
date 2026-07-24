package org.readybell.session.adapter.in.web;

import java.util.UUID;

/**
 * Minimal response body returned by {@code POST /api/sessions}.
 *
 * <p>Carries only the session identifier as specified by the Customer Session API contract
 * (Req 1.1, 1.3, 1.6).
 *
 * @param sessionId the newly created session's UUID
 */
public record StartSessionResponse(String sessionId) {

    /**
     * Convenience factory that converts a {@link UUID} to the string form expected
     * by the JSON response.
     *
     * @param id the session UUID; must not be null
     * @return a {@code StartSessionResponse} carrying the string representation of {@code id}
     */
    public static StartSessionResponse of(UUID id) {
        return new StartSessionResponse(id.toString());
    }
}
