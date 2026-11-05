package org.readybell.platform.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.readybell.platform.SpringDomainEventPublisherAdapter;
import org.readybell.shared.domain.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Delivers outbox events to the in-process handlers at least once.
 *
 * <p>Runs on a scheduler and is also woken right after a commit (low latency for live updates). Several nodes can
 * run it at the same time: {@link OutboxStore#claim} hands every event to one worker only. Handlers run
 * synchronously on the relay thread, so an exception is seen here and the event is retried with exponential
 * backoff; after {@code maxAttempts} it is parked and counted in {@code outbox.failed}.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxStore store;
    private final OutboxEventSerializer serializer;
    private final SpringDomainEventPublisherAdapter dispatcher;
    private final OutboxProperties props;
    private final Clock clock;

    private final AtomicBoolean running = new AtomicBoolean();
    private final ExecutorService wakeExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "outbox-wake");
        t.setDaemon(true);
        return t;
    });

    public OutboxRelay(OutboxStore store, OutboxEventSerializer serializer,
                       SpringDomainEventPublisherAdapter dispatcher, OutboxProperties props, Clock clock,
                       MeterRegistry meters) {
        this.store = store;
        this.serializer = serializer;
        this.dispatcher = dispatcher;
        this.props = props;
        this.clock = clock;
        Gauge.builder("outbox.pending", store, OutboxStore::countPending).register(meters);
        Gauge.builder("outbox.failed", store, OutboxStore::countFailed).register(meters);
    }

    /** Asks for a delivery round soon, without blocking the caller (used after a commit). */
    public void wake() {
        if (props.isEnabled()) {
            wakeExecutor.execute(this::drain);
        }
    }

    @Scheduled(fixedDelayString = "${readybell.outbox.poll-interval:1s}")
    public void scheduledDrain() {
        if (props.isEnabled()) {
            drain();
        }
    }

    /** One delivery round; rounds do not overlap inside a node. Returns how many events were handled. */
    public int drain() {
        if (!running.compareAndSet(false, true)) {
            return 0;
        }
        try {
            int handled = 0;
            while (true) {
                var batch = store.claim(clock.instant(), props.getLease(), props.getBatchSize());
                if (batch.isEmpty()) {
                    return handled;
                }
                for (var claimed : batch) {
                    deliver(claimed);
                    handled++;
                }
            }
        } catch (RuntimeException e) {
            log.warn("Outbox round failed: {}", e.getMessage());
            return 0;
        } finally {
            running.set(false);
        }
    }

    private void deliver(OutboxStore.Claimed claimed) {
        try {
            DomainEvent event = serializer.fromJson(claimed.type(), claimed.payload());
            dispatcher.publish(event);
            store.markProcessed(claimed.id(), clock.instant());
        } catch (RuntimeException e) {
            int attempt = claimed.attempts() + 1;
            boolean giveUp = attempt >= props.getMaxAttempts();
            var now = clock.instant();
            store.markFailed(claimed.id(), now, now.plus(backoff(attempt)), giveUp, String.valueOf(e));
            log.warn("Outbox event {} ({}) failed, attempt {}{}: {}", claimed.id(), claimed.type(), attempt,
                    giveUp ? ", giving up" : "", e.toString());
        }
    }

    Duration backoff(int attempt) {
        long factor = 1L << Math.min(attempt - 1, 20);
        Duration d = props.getInitialBackoff().multipliedBy(factor);
        return d.compareTo(props.getMaxBackoff()) > 0 ? props.getMaxBackoff() : d;
    }

    /** Deletes processed events older than the retention. */
    @Scheduled(cron = "${readybell.outbox.cleanup-cron:0 30 3 * * *}")
    public void cleanup() {
        int deleted = store.deleteProcessedBefore(clock.instant().minus(props.getRetention()));
        if (deleted > 0) {
            log.info("Outbox cleanup: removed {} processed event(s)", deleted);
        }
    }
}
