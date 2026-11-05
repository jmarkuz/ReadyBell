package org.readybell.platform.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.readybell.notification.domain.SendResult;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/**
 * Meters of the Web Push path: attempts by result, and the time from an order becoming READY to the push
 * service accepting the message (the "push within 2 s" success criterion).
 *
 * <p>The order-ready handler runs synchronously on the caller's thread, so the READY instant is carried in a
 * {@link ThreadLocal} for the duration of {@link #whileHandling}; the sender decorator reads it.
 */
@Component
public class PushMetrics {

    static final String ATTEMPTS = "readybell.push.attempts";
    static final String READY_LATENCY = "readybell.push.ready.latency";

    private final Map<SendResult, Counter> attempts = new EnumMap<>(SendResult.class);
    private final Counter errors;
    private final Timer readyLatency;
    private final Clock clock;
    private final ThreadLocal<Instant> readyAt = new ThreadLocal<>();

    public PushMetrics(MeterRegistry registry, Clock clock) {
        this.clock = clock;
        for (SendResult r : SendResult.values()) {
            attempts.put(r, Counter.builder(ATTEMPTS).tag("result", label(r))
                    .description("Web Push send attempts by outcome (retries count separately)").register(registry));
        }
        this.errors = Counter.builder(ATTEMPTS).tag("result", "failed")
                .description("Web Push send attempts by outcome (retries count separately)").register(registry);
        this.readyLatency = Timer.builder(READY_LATENCY)
                .description("Time from the order becoming READY to the push service accepting the push")
                .serviceLevelObjectives(Duration.ofMillis(250), Duration.ofMillis(500), Duration.ofSeconds(1),
                        Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(30))
                .register(registry);
    }

    /** Low-cardinality label: sent, failed (rejected or transient) or expired (subscription removed). */
    static String label(SendResult result) {
        return switch (result) {
            case ACCEPTED -> "sent";
            case ENDPOINT_EXPIRED -> "expired";
            case REJECTED, TRANSIENT_FAILURE -> "failed";
        };
    }

    void recordResult(SendResult result) {
        attempts.get(result).increment();
        if (result == SendResult.ACCEPTED) {
            Instant ready = readyAt.get();
            if (ready != null) {
                readyLatency.record(Duration.between(ready, clock.instant()).abs());
            }
        }
    }

    void recordError() {
        errors.increment();
    }

    /** Runs {@code work} (the handling of one OrderReady) with the READY instant available to the sender. */
    public void whileHandling(Instant readyInstant, Runnable work) {
        readyAt.set(readyInstant);
        try {
            work.run();
        } finally {
            readyAt.remove();
        }
    }
}
