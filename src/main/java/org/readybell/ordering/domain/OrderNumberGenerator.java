package org.readybell.ordering.domain;

import org.readybell.shared.domain.TenantId;

/**
 * Outbound port allocating the next per-restaurant order number (1, 2, 3 ...; never reset).
 *
 * <p>Implementations must be safe under concurrent callers and must take part in the caller's
 * transaction, so a rolled-back order does not burn a number and two orders never share one.
 */
public interface OrderNumberGenerator {

    /** Returns the next number for the restaurant; consecutive calls (per tenant) return consecutive values. */
    int next(TenantId tenantId);
}
