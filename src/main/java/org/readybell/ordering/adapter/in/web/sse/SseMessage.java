package org.readybell.ordering.adapter.in.web.sse;

import java.util.UUID;

/**
 * An order event as pushed to SSE clients (and, when enabled, relayed between nodes via Redis).
 *
 * <p>{@code order-status-updated} goes to the order's customer and to the restaurant's staff; {@code order-created} and {@code order-connected}
 * go to the staff only (a customer cannot have an open stream for an order that was just entered).
 * Messages relayed by a node that predates {@code event} are status updates.
 */
public record SseMessage(UUID orderId, UUID sessionId, UUID tenantId, String status, String event, String orderNumber) {

    public static final String STATUS_UPDATED = "order-status-updated";
    public static final String ORDER_CREATED = "order-created";
    public static final String ORDER_CONNECTED = "order-connected";

    public SseMessage {
        if (event == null) {
            event = STATUS_UPDATED;
        }
    }

    /** A status change. */
    public SseMessage(UUID orderId, UUID sessionId, UUID tenantId, String status) {
        this(orderId, sessionId, tenantId, status, STATUS_UPDATED, null);
    }

    /** A new order, with the number printed on the receipt (for example {@code #12}). */
    public static SseMessage created(UUID orderId, UUID sessionId, UUID tenantId, int orderNumber) {
        return new SseMessage(orderId, sessionId, tenantId, "CREATED", ORDER_CREATED, "#" + orderNumber);
    }

    /** The customer opened the tracking link (staff only): the phone is connected. */
    public static SseMessage connected(UUID orderId, UUID sessionId, UUID tenantId, int orderNumber) {
        return new SseMessage(orderId, sessionId, tenantId, "CREATED", ORDER_CONNECTED, "#" + orderNumber);
    }

    boolean forCustomers() {
        return STATUS_UPDATED.equals(event);
    }

    /**
     * Client-facing JSON payload: {@code {"id":"…","orderId":"…","status":"…"}}.
     * {@code id} lets the staff dashboard match its table rows; {@code orderId} is kept for the customer PWA.
     * A new order adds {@code event} and {@code orderNumber}.
     */
    public String payload() {
        String base = "\"id\":\"" + orderId + "\",\"orderId\":\"" + orderId + "\",\"status\":\"" + status + "\"";
        if (ORDER_CREATED.equals(event) || ORDER_CONNECTED.equals(event)) {
            return "{" + base + ",\"event\":\"" + event + "\",\"orderNumber\":\"" + orderNumber + "\"}";
        }
        return "{" + base + "}";
    }
}
