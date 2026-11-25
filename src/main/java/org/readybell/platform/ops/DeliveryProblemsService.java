package org.readybell.platform.ops;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.readybell.platform.PushDeliveryWorker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the operator needs when something gave up: the outbox events that were parked after their retries, and the pushes
 * that failed in the last day, each with the reason, and a way to try again. Nothing personal is shown: ids, types, counts
 * and error texts only (the text of a push is never listed).
 */
@Service
public class DeliveryProblemsService {

    private static final int LIMIT = 100;

    @PersistenceContext
    private EntityManager em;

    private final PushDeliveryWorker worker;
    private final Clock clock;
    private final Duration deadline;

    public DeliveryProblemsService(PushDeliveryWorker worker, Clock clock,
                                   @Value("${readybell.push.deadline:10m}") Duration deadline) {
        this.worker = worker;
        this.clock = clock;
        this.deadline = deadline;
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Map<String, Object> problems() {
        List<Map<String, Object>> events = new ArrayList<>();
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
                SELECT id, tenant_id, type, attempts, last_error, failed_at FROM outbox_event
                WHERE failed_at IS NOT NULL ORDER BY failed_at DESC LIMIT :limit""").setParameter("limit", LIMIT).getResultList()) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("id", r[0].toString());
            e.put("tenantId", r[1].toString());
            e.put("type", shortType((String) r[2]));
            e.put("attempts", r[3]);
            e.put("lastError", r[4]);
            e.put("failedAt", String.valueOf(r[5]));
            events.add(e);
        }
        List<Map<String, Object>> pushes = new ArrayList<>();
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
                SELECT id, tenant_id, order_id, attempts, last_error, created_at FROM notification
                WHERE status = 'FAILED' AND created_at > :since ORDER BY created_at DESC LIMIT :limit""")
                .setParameter("since", clock.instant().minus(Duration.ofDays(1))).setParameter("limit", LIMIT).getResultList()) {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("id", r[0].toString());
            n.put("tenantId", r[1].toString());
            n.put("orderId", r[2] == null ? null : r[2].toString());
            n.put("attempts", r[3]);
            n.put("lastError", r[4]);
            n.put("createdAt", String.valueOf(r[5]));
            pushes.add(n);
        }
        return Map.of("parkedEvents", events, "failedPushes", pushes);
    }

    /** Puts a parked outbox event back in the queue with a fresh set of attempts. @return 1, or 0 when it is not parked */
    @Transactional
    public int retryEvent(UUID id) {
        return em.createNativeQuery("""
                UPDATE outbox_event SET failed_at = NULL, attempts = 0, next_attempt_at = :now, locked_until = NULL, last_error = NULL
                WHERE id = :id AND failed_at IS NOT NULL""")
                .setParameter("now", clock.instant()).setParameter("id", id).executeUpdate();
    }

    /**
     * Queues a failed push again with a new deadline, unless there is nothing left to send (its text was removed by
     * retention) or the device is gone. @return 1, or 0 when it cannot be retried
     */
    @Transactional
    public int retryPush(UUID id) {
        Instant now = clock.instant();
        int n = em.createNativeQuery("""
                UPDATE notification SET status = 'PENDING', attempts = 0, next_attempt_at = :now, expires_at = :expires,
                       locked_until = NULL, last_error = NULL
                WHERE id = :id AND status = 'FAILED' AND payload <> ''
                  AND EXISTS (SELECT 1 FROM device_subscription s WHERE s.id = notification.subscription_id)""")
                .setParameter("now", now).setParameter("expires", now.plus(deadline)).setParameter("id", id).executeUpdate();
        if (n > 0) {
            worker.wake();
        }
        return n;
    }

    private static String shortType(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }
}
