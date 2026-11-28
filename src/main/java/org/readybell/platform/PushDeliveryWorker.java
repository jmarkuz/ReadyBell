package org.readybell.platform;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.readybell.notification.application.DeliveryTrigger;
import org.readybell.notification.application.PushDeliveryService;
import org.readybell.notification.domain.Notification;
import org.readybell.notification.domain.NotificationRepository;
import org.readybell.platform.metrics.PushMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Delivers the pushes that are due, in parallel and without ever waiting for a slow push service on a shared thread.
 *
 * <p>A round claims due notifications (row lease, see {@link NotificationRepository#claimDue}), at most as many as there
 * are free workers, and hands each to the worker pool; a pool thread makes the one attempt and records the outcome. A
 * push service that hangs for its timeout therefore occupies one pool thread, while the others keep delivering. Rounds
 * run when {@link #wake()} is called right after an order becomes READY, when a worker frees up, and on a schedule that
 * also picks up retries whose time has come. Several nodes can run it at once: the lease hands each notification to one.
 *
 * <p>{@code readybell.push.async=false} (tests) delivers on the calling thread, so results are visible at once, and
 * {@code readybell.push.scheduled=false} turns the schedule off. Tests share one database between cached application
 * contexts, and a scheduled worker of another context would otherwise take their notifications.
 */
@Component
public class PushDeliveryWorker implements DeliveryTrigger {
    private static final Logger log = LoggerFactory.getLogger(PushDeliveryWorker.class);

    private final NotificationRepository notifications;
    private final PushDeliveryService delivery;
    private final PushMetrics metrics;
    private final Clock clock;
    private final boolean async;
    private final boolean scheduled;
    private final int batchSize;
    private final Duration lease;

    private final Semaphore freeWorkers;
    private final int workers;
    private final AtomicBoolean running = new AtomicBoolean();
    private final ExecutorService pool;
    private final ExecutorService rounds = Executors.newSingleThreadExecutor(daemon("push-round"));

    public PushDeliveryWorker(NotificationRepository notifications, PushDeliveryService delivery, PushMetrics metrics,
                              Clock clock, MeterRegistry meters,
                              @Value("${readybell.push.async:true}") boolean async,
                              @Value("${readybell.push.scheduled:true}") boolean scheduled,
                              @Value("${readybell.push.workers:8}") int workers,
                              @Value("${readybell.push.batch-size:20}") int batchSize,
                              @Value("${readybell.push.lease:2m}") Duration lease) {
        this.notifications = notifications;
        this.delivery = delivery;
        this.metrics = metrics;
        this.clock = clock;
        this.async = async;
        this.scheduled = scheduled;
        this.workers = Math.max(1, workers);
        this.batchSize = Math.max(1, batchSize);
        this.lease = lease;
        this.freeWorkers = new Semaphore(this.workers);
        this.pool = async ? Executors.newFixedThreadPool(this.workers, daemon("push-delivery")) : null;
        Gauge.builder("readybell.push.pending", notifications, NotificationRepository::countPending)
                .description("Pushes waiting for a delivery attempt or a retry").register(meters);
    }

    private static java.util.concurrent.ThreadFactory daemon(String name) {
        var counter = new java.util.concurrent.atomic.AtomicInteger();
        return r -> {
            Thread t = new Thread(r, name + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    @Override
    public void wake() {
        if (async) {
            rounds.execute(this::drain);
        } else {
            drain();
        }
    }

    /** Picks up retries that have become due, and anything a missed wake-up left behind. */
    @Scheduled(fixedDelayString = "${readybell.push.poll-interval:1s}")
    public void scheduledDrain() {
        if (scheduled) {
            drain();
        }
    }

    /** One round: claims due notifications while workers are free. Returns how many were handed out. */
    public int drain() {
        if (!running.compareAndSet(false, true)) {
            return 0;
        }
        try {
            int handedOut = 0;
            while (true) {
                int room = Math.min(batchSize, freeWorkers.availablePermits());
                if (room == 0) {
                    return handedOut;       // every worker is busy; the one that finishes first asks for another round
                }
                List<Notification> batch = notifications.claimDue(clock.instant(), lease, room);
                if (batch.isEmpty()) {
                    return handedOut;
                }
                for (Notification notification : batch) {
                    freeWorkers.acquireUninterruptibly();
                    if (async) {
                        pool.execute(() -> deliver(notification));
                    } else {
                        deliver(notification);
                    }
                    handedOut++;
                }
            }
        } catch (RuntimeException e) {
            log.warn("Push delivery round failed: {}", e.toString());
            return 0;
        } finally {
            running.set(false);
        }
    }

    private void deliver(Notification notification) {
        try {
            // the READY instant lets the sender's metrics measure "ready to push accepted"
            metrics.whileHandling(notification.readyAt() != null ? notification.readyAt() : clock.instant(),
                    () -> delivery.attempt(notification));
        } catch (RuntimeException e) {
            // the lease expires and the notification is handed out again
            log.warn("Push delivery of {} failed unexpectedly: {}", notification.id(), e.toString());
        } finally {
            freeWorkers.release();
            if (async) {
                rounds.execute(this::drain);
            }
        }
    }

    @PreDestroy
    void shutdown() {
        rounds.shutdown();
        if (pool != null) {
            pool.shutdown();
        }
    }
}
