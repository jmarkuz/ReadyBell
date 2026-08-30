package com.example.shared.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Shared-kernel value object identifying a single tenant (Restaurant).
 *
 * <p>Wraps a non-null {@link UUID}. Every tenant-scoped record and request is
 * associated with exactly one {@code TenantId} (Req 10.1).
 */
public record TenantId(UUID value) {
    public TenantId {
        Objects.requireNonNull(value, "tenantId must not be null");
    }
}
