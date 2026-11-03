package org.readybell.ordering.adapter.in.web;

import org.readybell.ordering.domain.OrderStatusUpdated;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.TenantId;
import org.readybell.shared.tenancy.TenantContextHolder;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Inbound SSE adapter that pushes live order-state updates to PWA customers (Req 2.3, ≤5s)
 * and Restaurant Staff panel clients (Req 7.4, ≤10s).
 *
 * <p>Two streams are exposed:
 * <ul>
 *   <li>{@code GET /api/sessions/{sessionId}/orders/stream} — customer stream scoped to one
 *       session; emitters are keyed by {@link CustomerSessionId}.</li>
 *   <li>{@code GET /api/orders/stream} — staff-panel stream scoped to the authenticated
 *       tenant; emitters are keyed by {@link TenantId}.</li>
 * </ul>
 *
 * <p>When an {@link OrderStatusUpdated} domain event is published in-process the
 * {@link #onOrderStatusUpdated(OrderStatusUpdated)} listener fires synchronously on the
 * application thread and fans the update out to all matching open emitters. Each emitter
 * receives a JSON payload:
 * <pre>{"orderId":"&lt;uuid&gt;","status":"&lt;LIFECYCLE_STATE&gt;"}</pre>
 *
 * <p>Emitter registries are kept as {@link ConcurrentHashMap}s of
 * {@link CopyOnWriteArraySet}s so that concurrent subscribe/unsubscribe and event-push
 * operations are thread-safe without locking the whole map.
 *
 * <p>Every emitter registers completion, timeout, and error callbacks that remove it from
 * its registry to prevent memory leaks and IO writes to closed connections.
 */
@RestController
public class OrderSseController {

    /** Customer-facing SSE registry: one set of emitters per customer session. */
    private final ConcurrentHashMap<CustomerSessionId, Set<SseEmitter>> customerEmitters =
            new ConcurrentHashMap<>();

    /** Staff-panel SSE registry: one set of emitters per tenant. */
    private final ConcurrentHashMap<TenantId, Set<SseEmitter>> staffEmitters =
            new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Customer stream — /api/sessions/{sessionId}/orders/stream
    // -------------------------------------------------------------------------

    /**
     * Opens an SSE stream for a customer session (Req 2.3, ≤5 s latency).
     *
     * <p>No authentication is required — the session ID in the path is the customer's
     * opaque handle to their own order list, consistent with the session-scoped public
     * endpoints used by the PWA.
     *
     * @param sessionId the customer session UUID from the path
     * @return an {@link SseEmitter} with no timeout; the caller's SSE connection stays open
     *         until the client disconnects or the server completes the emitter
     */
    @GetMapping("/api/sessions/{sessionId}/orders/stream")
    public SseEmitter customerOrderStream(@PathVariable("sessionId") UUID sessionId) {
        CustomerSessionId id = new CustomerSessionId(sessionId);
        SseEmitter emitter = new SseEmitter(0L); // infinite timeout

        Set<SseEmitter> emitters = customerEmitters
                .computeIfAbsent(id, k -> new CopyOnWriteArraySet<>());
        emitters.add(emitter);

        Runnable cleanup = () -> removeCustomerEmitter(id, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ex -> removeCustomerEmitter(id, emitter));

        return emitter;
    }

    private void removeCustomerEmitter(CustomerSessionId sessionId, SseEmitter emitter) {
        Set<SseEmitter> emitters = customerEmitters.get(sessionId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                customerEmitters.remove(sessionId, emitters);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Staff-panel stream — /api/orders/stream
    // -------------------------------------------------------------------------

    /**
     * Opens an SSE stream for the authenticated tenant's orders (Req 7.4, ≤10 s latency).
     *
     * <p>Requires the caller to be authenticated (tenant context is resolved by the
     * inbound filter before this method is invoked).
     *
     * @return an {@link SseEmitter} with no timeout scoped to the current tenant
     */
    @GetMapping("/api/orders/stream")
    public SseEmitter staffOrderStream() {
        TenantId tenantId = TenantContextHolder.require();
        SseEmitter emitter = new SseEmitter(0L); // infinite timeout

        Set<SseEmitter> emitters = staffEmitters
                .computeIfAbsent(tenantId, k -> new CopyOnWriteArraySet<>());
        emitters.add(emitter);

        Runnable cleanup = () -> removeStaffEmitter(tenantId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ex -> removeStaffEmitter(tenantId, emitter));

        return emitter;
    }

    private void removeStaffEmitter(TenantId tenantId, SseEmitter emitter) {
        Set<SseEmitter> emitters = staffEmitters.get(tenantId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                staffEmitters.remove(tenantId, emitters);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Event listener — push to matching emitters on OrderStatusUpdated
    // -------------------------------------------------------------------------

    /**
     * Handles {@link OrderStatusUpdated} domain events and fans updates out to all
     * matching open SSE emitters (Req 2.3, 7.4).
     *
     * <p>The payload pushed to each emitter is a JSON object:
     * <pre>{"orderId":"&lt;uuid&gt;","status":"&lt;NEW_STATUS&gt;"}</pre>
     *
     * <p>Customer emitters are matched by {@link CustomerSessionId}. Staff-panel emitters
     * are matched by {@link TenantId}. If sending to an individual emitter fails the
     * emitter is removed from the registry to prevent future write attempts; the failure
     * does not affect other emitters or the event handling flow.
     *
     * @param event the published {@link OrderStatusUpdated} event
     */
    @EventListener
    public void onOrderStatusUpdated(OrderStatusUpdated event) {
        Objects.requireNonNull(event, "event must not be null");

        String payload = buildPayload(event);

        // Push to matching customer emitters
        Set<SseEmitter> customers = customerEmitters.get(event.sessionId());
        if (customers != null) {
            for (SseEmitter emitter : customers) {
                sendOrRemove(emitter, payload,
                        () -> removeCustomerEmitter(event.sessionId(), emitter));
            }
        }

        // Push to matching staff-panel emitters
        Set<SseEmitter> staff = staffEmitters.get(event.tenantId());
        if (staff != null) {
            for (SseEmitter emitter : staff) {
                sendOrRemove(emitter, payload,
                        () -> removeStaffEmitter(event.tenantId(), emitter));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static String buildPayload(OrderStatusUpdated event) {
        // Construct a simple JSON string without pulling in Jackson explicitly
        return "{\"orderId\":\"" + event.orderId().value() + "\","
                + "\"status\":\"" + event.toStatus().name() + "\"}";
    }

    private static void sendOrRemove(SseEmitter emitter, String payload, Runnable cleanup) {
        try {
            emitter.send(SseEmitter.event()
                    .name("order-status-updated")
                    .data(payload));
        } catch (IOException | IllegalStateException ex) {
            // Emitter is closed or in an error state — remove it from the registry
            cleanup.run();
        }
    }
}
