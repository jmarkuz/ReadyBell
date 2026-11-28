package org.readybell.session.adapter.in.web;

import java.util.UUID;

/**
 * Minimal response body returned by {@code POST /api/sessions}.
 *
 * <p>Carries only the session identifier as specified by the Customer Session API contract.
 */
public record StartSessionResponse(String sessionId) {
    /**
     * Convenience factory that converts a {@link UUID} to the string form expected
     * by the JSON response.
     */
    public static StartSessionResponse of(UUID id) {
        return new StartSessionResponse(id.toString());
    }
}
