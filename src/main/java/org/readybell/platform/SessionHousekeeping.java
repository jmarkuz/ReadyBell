package org.readybell.platform;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Keeps customer sessions from piling up: a session older than the TTL whose orders are all finished
 * is ENDED (no new orders can join it), and the push subscriptions of ended sessions are deleted —
 * they are personal device endpoints that are useless once the order has been picked up.
 * Orders themselves are kept.
 */
@Component
public class SessionHousekeeping {

    private static final Logger log = LoggerFactory.getLogger(SessionHousekeeping.class);

    @PersistenceContext private EntityManager em;
    private final Clock clock;
    private final Duration ttl;

    public SessionHousekeeping(Clock clock, @Value("${readybell.session.ttl:24h}") Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
    }

    @Scheduled(cron = "${readybell.session.cleanup-cron:0 15 * * * *}")
    public void scheduledRun() {
        run(ttl);
    }

    /** @return {ended sessions, deleted subscriptions} */
    @Transactional
    public int[] run(Duration olderThan) {
        Instant cutoff = clock.instant().minus(olderThan);
        int ended = em.createQuery("""
                UPDATE CustomerSessionJpaEntity s SET s.status = org.readybell.session.domain.SessionStatus.ENDED
                WHERE s.status = org.readybell.session.domain.SessionStatus.ACTIVE AND s.startedAt < :cutoff
                  AND NOT EXISTS (SELECT 1 FROM OrderJpaEntity o WHERE o.sessionId = s.id
                      AND o.status IN (org.readybell.ordering.domain.OrderStatus.CREATED,
                                       org.readybell.ordering.domain.OrderStatus.IN_PROGRESS,
                                       org.readybell.ordering.domain.OrderStatus.READY))
                """).setParameter("cutoff", cutoff).executeUpdate();
        int subscriptions = em.createQuery("""
                DELETE FROM DeviceSubscriptionJpaEntity d WHERE d.sessionId IN
                  (SELECT s.id FROM CustomerSessionJpaEntity s WHERE s.status = org.readybell.session.domain.SessionStatus.ENDED)
                """).executeUpdate();
        if (ended > 0 || subscriptions > 0) {
            log.info("Session housekeeping: ended {} session(s), removed {} push subscription(s)", ended, subscriptions);
        }
        return new int[] {ended, subscriptions};
    }
}
