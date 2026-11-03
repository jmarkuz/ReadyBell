package org.readybell.ordering.adapter.in.web.sse;

import java.util.UUID;

/** Order status change as pushed to SSE clients (and, when enabled, relayed between nodes via Redis). */
public record SseMessage(UUID orderId, UUID sessionId, UUID tenantId, String status) {

    /**
     * Client-facing JSON payload: {@code {"id":"…","orderId":"…","status":"…"}}.
     * {@code id} lets the staff dashboard match its table rows; {@code orderId} is kept for the customer PWA.
     */
    public String payload() {
        return "{\"id\":\"" + orderId + "\",\"orderId\":\"" + orderId + "\",\"status\":\"" + status + "\"}";
    }
}
