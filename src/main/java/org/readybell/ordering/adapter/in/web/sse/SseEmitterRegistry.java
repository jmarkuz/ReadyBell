package org.readybell.ordering.adapter.in.web.sse;

import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.TenantId;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Open SSE connections of <em>this</em> node: one set per customer session and one per tenant
 * (staff panel). Emitters remove themselves on completion, timeout or error, and are dropped
 * when a send fails, so closed connections never leak.
 */
@Component
public class SseEmitterRegistry {

    private final ConcurrentHashMap<UUID, Set<SseEmitter>> customerEmitters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Set<SseEmitter>> staffEmitters = new ConcurrentHashMap<>();

    public SseEmitter registerCustomer(CustomerSessionId sessionId) {
        return register(customerEmitters, sessionId.value());
    }

    public SseEmitter registerStaff(TenantId tenantId) {
        return register(staffEmitters, tenantId.value());
    }

    /** Pushes {@code message} to every matching local connection. */
    public void deliver(SseMessage message) {
        if (message.forCustomers()) {
            send(customerEmitters, message.sessionId(), message);
        }
        send(staffEmitters, message.tenantId(), message);
    }

    /** Number of open connections (for tests and diagnostics). */
    /**
     * Keeps idle streams alive through proxies and load balancers (which close silent connections)
     * and drops the ones whose client has gone away.
     */
    @Scheduled(fixedDelayString = "${readybell.sse.heartbeat:20s}")
    public void heartbeat() {
        ping(customerEmitters);
        ping(staffEmitters);
    }

    private void ping(ConcurrentHashMap<UUID, Set<SseEmitter>> registry) {
        registry.forEach((key, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException ex) {
                    remove(registry, key, emitter);
                }
            }
        });
    }

    public int customerConnections() {
        return customerEmitters.values().stream().mapToInt(Set::size).sum();
    }

    public int staffConnections() {
        return staffEmitters.values().stream().mapToInt(Set::size).sum();
    }

    public int openConnections() {
        return customerEmitters.values().stream().mapToInt(Set::size).sum()
                + staffEmitters.values().stream().mapToInt(Set::size).sum();
    }

    /** Registers an externally created emitter for a customer session (used by tests). */
    public SseEmitter registerCustomer(CustomerSessionId sessionId, SseEmitter emitter) {
        return attach(customerEmitters, sessionId.value(), emitter);
    }

    /** Registers an externally created emitter for a restaurant's staff panel (used by tests). */
    public SseEmitter registerStaff(TenantId tenantId, SseEmitter emitter) {
        return attach(staffEmitters, tenantId.value(), emitter);
    }

    private SseEmitter register(ConcurrentHashMap<UUID, Set<SseEmitter>> registry, UUID key) {
        return attach(registry, key, new SseEmitter(0L)); // no server-side timeout
    }

    private SseEmitter attach(ConcurrentHashMap<UUID, Set<SseEmitter>> registry, UUID key, SseEmitter emitter) {
        registry.computeIfAbsent(key, k -> new CopyOnWriteArraySet<>()).add(emitter);
        Runnable cleanup = () -> remove(registry, key, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ex -> cleanup.run());
        // Commit the response headers right away: without a first write the browser's EventSource
        // never fires `open` (nor do proxies start streaming) until the first real event.
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException | IllegalStateException e) {
            cleanup.run();
        }
        return emitter;
    }

    private void send(ConcurrentHashMap<UUID, Set<SseEmitter>> registry, UUID key, SseMessage message) {
        Set<SseEmitter> emitters = registry.get(key);
        if (emitters == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(message.event()).data(message.payload()));
            } catch (IOException | IllegalStateException ex) {
                remove(registry, key, emitter);
            }
        }
    }

    private void remove(ConcurrentHashMap<UUID, Set<SseEmitter>> registry, UUID key, SseEmitter emitter) {
        Set<SseEmitter> emitters = registry.get(key);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                registry.remove(key, emitters);
            }
        }
    }
}
