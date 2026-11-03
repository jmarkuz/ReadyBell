package org.readybell.ordering.adapter.in.web.sse;

import java.util.UUID;

/** Order status change as pushed to SSE clients (and, when enabled, relayed between nodes via Redis). */
public record SseMessage(UUID orderId, UUID sessionId, UUID tenantId, String status) {

    /** Client-facing JSON payload: {@code {"orderId":"…","status":"…"}}. */
    public String payload() {
        return "{\"orderId\":\"" + orderId + "\",\"status\":\"" + status + "\"}";
    }
}
