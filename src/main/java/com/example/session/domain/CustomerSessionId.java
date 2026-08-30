package com.example.session.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity value object for a {@link CustomerSession}.
 *
 * <p>Each scan of a QR code produces a distinct {@code CustomerSessionId},
 * guaranteeing that no two sessions share the same identity (Req 1.1).
 */
public record CustomerSessionId(UUID value) {

    public CustomerSessionId {
        Objects.requireNonNull(value, "customerSessionId must not be null");
    }

    /** Creates a new, random {@code CustomerSessionId}. */
    public static CustomerSessionId newId() {
        return new CustomerSessionId(UUID.randomUUID());
    }
}
