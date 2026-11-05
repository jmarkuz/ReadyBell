package org.readybell.ordering.domain;

import org.readybell.shared.domain.TenantId;

import java.util.HashMap;
import java.util.Map;

/** In-memory fake of {@link OrderNumberGenerator}: 1, 2, 3 ... per tenant. */
public class InMemoryOrderNumberGenerator implements OrderNumberGenerator {

    private final Map<TenantId, Integer> last = new HashMap<>();

    @Override
    public synchronized int next(TenantId tenantId) {
        return last.merge(tenantId, 1, Integer::sum);
    }
}
