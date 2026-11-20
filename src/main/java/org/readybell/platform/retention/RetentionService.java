package org.readybell.platform.retention;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Keeps personal data no longer than needed (docs/DATA_RETENTION_PLAN.md): a finished order loses its customer name, its
 * item names and the link to the customer session after {@code readybell.retention.order-days}; old notification texts
 * and ended sessions go too. What the business needs later (timings, outcome, size) lives in {@code order_fact} and
 * {@code order_status_history}, which carry nothing personal and are written when the order ends.
 *
 * <p>Every task works in batches, each in its own short transaction, and is idempotent: a run that stops half way is
 * continued by the next one. It may run on every replica; the first to take the PostgreSQL advisory lock does a batch.
 * With {@code readybell.retention.dry-run=true} nothing is changed and each task only reports how many rows it would.
 * Every run of a task is recorded in {@code retention_run}.
 */
@Component
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    /** Transaction-level advisory lock key ("RETENTIO"): one replica at a time works on a batch. */
    public static final long LOCK_KEY = 0x5245_5445_4E54_494FL;

    private static final List<String> FINISHED = List.of("COMPLETED", "CANCELLED");

    @PersistenceContext
    private EntityManager em;

    private final TransactionTemplate tx;
    private final Clock clock;
    private final MeterRegistry meters;
    private final boolean enabled;
    private final boolean dryRun;
    private final int batchSize;
    private final Duration orderAge;
    private final Duration notificationAge;
    private final Duration sessionAge;
    private final AtomicLong lastSuccess;

    public RetentionService(PlatformTransactionManager transactionManager, Clock clock, MeterRegistry meters,
                            @Value("${readybell.retention.enabled:true}") boolean enabled,
                            @Value("${readybell.retention.dry-run:false}") boolean dryRun,
                            @Value("${readybell.retention.batch-size:500}") int batchSize,
                            @Value("${readybell.retention.order-days:30}") int orderDays,
                            @Value("${readybell.retention.notification-days:30}") int notificationDays,
                            @Value("${readybell.retention.session-days:30}") int sessionDays) {
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.meters = meters;
        this.enabled = enabled;
        this.dryRun = dryRun;
        this.batchSize = requireBetween("readybell.retention.batch-size", batchSize, 1, 10_000);
        this.orderAge = Duration.ofDays(requireBetween("readybell.retention.order-days", orderDays, 7, 365));
        this.notificationAge = Duration.ofDays(requireBetween("readybell.retention.notification-days", notificationDays, 7, 365));
        this.sessionAge = Duration.ofDays(requireBetween("readybell.retention.session-days", sessionDays, 7, 365));
        // starts at "now": the alert fires when no run has succeeded for two days after the start
        this.lastSuccess = new AtomicLong(clock.instant().getEpochSecond());
        Gauge.builder("readybell.retention.last.success.timestamp", lastSuccess, AtomicLong::get)
                .description("Unix time of the last retention run that finished without an error").register(meters);
    }

    private static int requireBetween(String property, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(property + " must be between " + min + " and " + max + " but is " + value);
        }
        return value;
    }

    /** The nightly run. */
    @Scheduled(cron = "${readybell.retention.cron:0 30 2 * * *}")
    public void scheduledRun() {
        if (enabled) {
            run(clock.instant(), null);
        }
    }

    /**
     * Runs every task. {@code tenant} limits the work to one restaurant (tests, offboarding); {@code null} means all.
     *
     * @return rows changed by each task (rows that would change, in a dry run)
     */
    public Map<String, Integer> run(Instant now, UUID tenant) {
        Map<String, Integer> result = new LinkedHashMap<>();
        boolean clean = true;
        // facts first: an order is only anonymized once its fact row exists
        clean &= task("backfill-facts", result, () -> backfillFacts(now.minus(orderAge), tenant));
        clean &= task("anonymize-orders", result, () -> anonymizeOrders(now.minus(orderAge), tenant, now));
        clean &= task("scrub-notifications", result, () -> scrubNotifications(now.minus(notificationAge), tenant));
        clean &= task("purge-sessions", result, () -> purgeSessions(now.minus(sessionAge), tenant));
        if (clean) {
            lastSuccess.set(clock.instant().getEpochSecond());
        }
        log.info("Retention run{}: {}", dryRun ? " (dry run)" : "", result);
        return result;
    }

    private interface Work {
        int run();
    }

    private boolean task(String name, Map<String, Integer> result, Work work) {
        UUID runId = UUID.randomUUID();
        Instant started = clock.instant();
        tx.executeWithoutResult(s -> em.createNativeQuery("""
                INSERT INTO retention_run (id, task, started_at, affected, dry_run) VALUES (:id, :task, :started, 0, :dry)
                """).setParameter("id", runId).setParameter("task", name).setParameter("started", started)
                .setParameter("dry", dryRun).executeUpdate());
        int affected = 0;
        String error = null;
        try {
            affected = work.run();
        } catch (RuntimeException e) {
            error = e.toString().substring(0, Math.min(255, e.toString().length()));
            log.warn("Retention task {} failed: {}", name, e.toString());
        }
        result.put(name, affected);
        if (affected > 0) {
            Counter.builder("readybell.retention.rows").tag("task", name).tag("dry_run", String.valueOf(dryRun))
                    .description("Rows changed by the retention tasks").register(meters).increment(affected);
        }
        int finalAffected = affected;
        String finalError = error;
        tx.executeWithoutResult(s -> em.createNativeQuery("""
                UPDATE retention_run SET finished_at = :finished, affected = :affected, error = :error WHERE id = :id
                """).setParameter("finished", clock.instant()).setParameter("affected", finalAffected)
                .setParameter("error", finalError).setParameter("id", runId).executeUpdate());
        return error == null;
    }

    // ---------------------------------------------------------------------------------------------------------------

    /** Writes the fact row of finished orders that predate the fact table (their timings are estimates). */
    int backfillFacts(Instant cutoff, UUID tenant) {
        return batches(ids(tenant, """
                SELECT o.id FROM "order" o
                WHERE o.status IN ('COMPLETED', 'CANCELLED') AND o.anonymized_at IS NULL AND o.updated_at < :cutoff
                  AND NOT EXISTS (SELECT 1 FROM order_fact f WHERE f.order_id = o.id)%s
                ORDER BY o.updated_at, o.id LIMIT :batch OFFSET :offset""", "o", cutoff),
                this::writeFacts);
    }

    /** Removes the customer name, the item names and the link to the session from finished orders that have a fact row. */
    int anonymizeOrders(Instant cutoff, UUID tenant, Instant now) {
        return batches(ids(tenant, """
                SELECT o.id FROM "order" o
                WHERE o.status IN ('COMPLETED', 'CANCELLED') AND o.anonymized_at IS NULL AND o.updated_at < :cutoff
                  AND EXISTS (SELECT 1 FROM order_fact f WHERE f.order_id = o.id)%s
                ORDER BY o.updated_at, o.id LIMIT :batch OFFSET :offset""", "o", cutoff),
                ids -> anonymize(ids, now));
    }

    /** Fact rows for these finished orders when they have none (timings estimated: no history was recorded). Runs in the caller's transaction. */
    int writeFacts(List<UUID> ids) {
        return em.createNativeQuery("""
                INSERT INTO order_fact (order_id, tenant_id, created_at, in_progress_at, ready_at, finished_at,
                                        outcome, item_count, quantity, connected, approximate)
                SELECT o.id, o.tenant_id, o.created_at, NULL, NULL, o.updated_at, o.status,
                       (SELECT count(*) FROM order_item i WHERE i.order_id = o.id),
                       COALESCE((SELECT sum(i.quantity) FROM order_item i WHERE i.order_id = o.id), 0),
                       o.connected_at IS NOT NULL, true
                FROM "order" o WHERE o.id IN (:ids)
                ON CONFLICT (order_id) DO NOTHING""").setParameter("ids", ids).executeUpdate();
    }

    /**
     * Removes the personal data of these finished orders. The session id is replaced by a random one, so the customer's
     * tracking link stops working; the items go, the fact row stays. Runs in the caller's transaction.
     */
    int anonymize(List<UUID> ids, Instant now) {
        int orders = em.createNativeQuery("""
                UPDATE "order" SET customer_name = NULL, session_id = gen_random_uuid(), anonymized_at = :now
                WHERE id IN (:ids)""").setParameter("now", now).setParameter("ids", ids).executeUpdate();
        em.createNativeQuery("DELETE FROM order_item WHERE order_id IN (:ids)").setParameter("ids", ids).executeUpdate();
        return orders;
    }

    /** Empties the texts of notifications that are no longer waiting for delivery; the status and times stay. */
    int scrubNotifications(Instant cutoff, UUID tenant) {
        return batches(ids(tenant, """
                SELECT n.id FROM notification n
                WHERE n.created_at < :cutoff AND n.status <> 'PENDING' AND n.payload <> ''%s
                ORDER BY n.created_at, n.id LIMIT :batch OFFSET :offset""", "n", cutoff),
                ids -> em.createNativeQuery("""
                        UPDATE notification SET payload = '', title = NULL, url = NULL, last_error = NULL
                        WHERE id IN (:ids)""").setParameter("ids", ids).executeUpdate());
    }

    /** Deletes ended sessions no order refers to any more (and any push subscription left on them). */
    int purgeSessions(Instant cutoff, UUID tenant) {
        return batches(ids(tenant, """
                SELECT s.id FROM customer_session s
                WHERE s.status = 'ENDED' AND s.started_at < :cutoff
                  AND NOT EXISTS (SELECT 1 FROM "order" o WHERE o.session_id = s.id)%s
                ORDER BY s.started_at, s.id LIMIT :batch OFFSET :offset""", "s", cutoff),
                ids -> {
                    em.createNativeQuery("DELETE FROM device_subscription WHERE session_id IN (:ids)").setParameter("ids", ids).executeUpdate();
                    return em.createNativeQuery("DELETE FROM customer_session WHERE id IN (:ids)").setParameter("ids", ids).executeUpdate();
                });
    }

    // ---------------------------------------------------------------------------------------------------------------

    private interface IdSource {
        /** @param offset rows to skip: always 0 when changing (a changed row leaves the result), growing in a dry run */
        List<UUID> next(int offset);
    }

    private interface Change {
        int apply(List<UUID> ids);
    }

    /** The next batch of candidate ids; the SQL has a {@code %s} where an optional tenant condition goes. */
    private IdSource ids(UUID tenant, String sql, String alias, Instant cutoff) {
        String query = sql.formatted(tenant == null ? "" : " AND " + alias + ".tenant_id = :tenant")
                + (dryRun ? "" : " FOR UPDATE OF " + alias + " SKIP LOCKED");
        return offset -> {
            var q = em.createNativeQuery(query).setParameter("cutoff", cutoff).setParameter("batch", batchSize)
                    .setParameter("offset", offset);
            if (tenant != null) {
                q.setParameter("tenant", tenant);
            }
            @SuppressWarnings("unchecked")
            List<Object> rows = q.getResultList();
            return rows.stream().map(o -> o instanceof UUID u ? u : UUID.fromString(o.toString())).toList();
        };
    }

    /** Applies {@code change} to batch after batch until nothing is left; a dry run only counts the rows. */
    private int batches(IdSource source, Change change) {
        int total = 0;
        if (dryRun) {
            return count(source);
        }
        while (true) {
            Integer done = tx.execute(s -> {
                Boolean locked = (Boolean) em.createNativeQuery("SELECT pg_try_advisory_xact_lock(:key)")
                        .setParameter("key", LOCK_KEY).getSingleResult();
                if (!Boolean.TRUE.equals(locked)) {
                    return -1;                       // another replica holds it; its work is the same work
                }
                List<UUID> ids = source.next(0);
                if (ids.isEmpty()) {
                    return 0;
                }
                change.apply(ids);
                return ids.size();
            });
            if (done == null || done <= 0) {
                return total;
            }
            total += done;
        }
    }

    /** A dry run: the number of rows the task would change, counted batch by batch without locking or changing anything. */
    private int count(IdSource source) {
        int total = 0;
        while (true) {
            int offset = total;
            Integer n = tx.execute(s -> source.next(offset).size());
            if (n == null || n == 0) {
                return total;
            }
            total += n;
        }
    }
}
