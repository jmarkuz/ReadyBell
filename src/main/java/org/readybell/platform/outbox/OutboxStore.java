package org.readybell.platform.outbox;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.readybell.shared.domain.DomainEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Database access of the outbox. Every method runs in its own (or the caller's) transaction. */
@Component
public class OutboxStore {

    /** A claimed event, ready to be dispatched. */
    public record Claimed(UUID id, String type, String payload, int attempts) {}

    @PersistenceContext
    private EntityManager em;

    /** Adds an event in the caller's transaction, so it commits or rolls back together with the business change. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(DomainEvent event, String json, Instant now) {
        em.persist(new OutboxEventEntity(event.eventId(), event.tenantId().value(),
                event.getClass().getName(), json, now));
    }

    /**
     * Claims up to {@code limit} due events: row-locked with {@code FOR UPDATE SKIP LOCKED} so concurrent relays
     * (other nodes) never take the same row, then leased for {@code lease} so the locks can be released while the
     * handlers run. A worker that dies simply lets the lease expire.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public List<Claimed> claim(Instant now, Duration lease, int limit) {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT id, type, payload, attempts FROM outbox_event
                WHERE processed_at IS NULL AND failed_at IS NULL AND next_attempt_at <= :now
                  AND (locked_until IS NULL OR locked_until <= :now)
                ORDER BY created_at, id
                LIMIT :limit FOR UPDATE SKIP LOCKED
                """).setParameter("now", now).setParameter("limit", limit).getResultList();
        Instant until = now.plus(lease);
        return rows.stream().map(r -> {
            UUID id = toUuid(r[0]);
            em.createNativeQuery("UPDATE outbox_event SET locked_until = :until WHERE id = :id")
                    .setParameter("until", until).setParameter("id", id).executeUpdate();
            return new Claimed(id, (String) r[1], (String) r[2], ((Number) r[3]).intValue());
        }).toList();
    }

    @Transactional
    public void markProcessed(UUID id, Instant now) {
        em.createNativeQuery("""
                UPDATE outbox_event SET processed_at = :now, locked_until = NULL, attempts = attempts + 1,
                       last_error = NULL WHERE id = :id
                """).setParameter("now", now).setParameter("id", id).executeUpdate();
    }

    /** Records a failed attempt; schedules the next one, or parks the event as failed when {@code giveUp}. */
    @Transactional
    public void markFailed(UUID id, Instant now, Instant nextAttempt, boolean giveUp, String error) {
        em.createNativeQuery("""
                UPDATE outbox_event SET attempts = attempts + 1, locked_until = NULL, last_error = :error,
                       next_attempt_at = :next, failed_at = CASE WHEN :giveUp THEN :now ELSE NULL END
                WHERE id = :id
                """).setParameter("error", truncate(error)).setParameter("next", nextAttempt)
                .setParameter("giveUp", giveUp).setParameter("now", now).setParameter("id", id).executeUpdate();
    }

    @Transactional
    public int deleteProcessedBefore(Instant cutoff) {
        return em.createNativeQuery("DELETE FROM outbox_event WHERE processed_at IS NOT NULL AND processed_at < :cutoff")
                .setParameter("cutoff", cutoff).executeUpdate();
    }

    @Transactional(readOnly = true)
    public long countPending() {
        return ((Number) em.createNativeQuery(
                "SELECT count(*) FROM outbox_event WHERE processed_at IS NULL AND failed_at IS NULL")
                .getSingleResult()).longValue();
    }

    @Transactional(readOnly = true)
    public long countFailed() {
        return ((Number) em.createNativeQuery("SELECT count(*) FROM outbox_event WHERE failed_at IS NOT NULL")
                .getSingleResult()).longValue();
    }

    /** Postgres returns a native uuid column as UUID, H2 as bytes. */
    private static UUID toUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof byte[] bytes && bytes.length == 16) {
            var buffer = java.nio.ByteBuffer.wrap(bytes);
            return new UUID(buffer.getLong(), buffer.getLong());
        }
        return UUID.fromString(String.valueOf(value));
    }

    private static String truncate(String s) {
        return s == null ? null : s.length() > 2000 ? s.substring(0, 2000) : s;
    }
}
