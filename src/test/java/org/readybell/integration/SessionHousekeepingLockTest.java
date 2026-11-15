package org.readybell.integration;

import org.junit.jupiter.api.Test;
import org.readybell.notification.domain.NotificationSender;
import org.readybell.platform.SessionHousekeeping;
import org.readybell.session.domain.CustomerSession;
import org.readybell.session.domain.CustomerSessionRepository;
import org.readybell.shared.domain.TenantId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/** With several replicas only one runs the housekeeping round; the rest skip it. */
@SpringBootTest
@ActiveProfiles("test")
class SessionHousekeepingLockTest {

    private static final TenantId TENANT = new TenantId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));

    @Autowired private SessionHousekeeping housekeeping;
    @Autowired private CustomerSessionRepository sessions;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate tx;
    @MockBean private NotificationSender notificationSender;

    private String statusOf(UUID sessionId) {
        return jdbc.queryForObject("SELECT status FROM customer_session WHERE id = ?", String.class, sessionId);
    }

    @Test
    void aRoundIsSkippedWhileAnotherReplicaHoldsTheLockAndRunsOnceItIsFree() throws Exception {
        var session = CustomerSession.start(TENANT, Clock.systemUTC());
        sessions.save(session);
        UUID id = session.getId().value();

        var otherReplica = Executors.newSingleThreadExecutor();
        try {
            int[] whileLocked = tx.execute(status -> {
                jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> {}, SessionHousekeeping.LOCK_KEY);   // "replica A" is running
                try {
                    return otherReplica.submit(() -> housekeeping.run(Duration.ofNanos(1))).get();       // "replica B" tries
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });

            assertThat(whileLocked).containsExactly(0, 0);
            assertThat(statusOf(id)).isEqualTo("ACTIVE");
        } finally {
            otherReplica.shutdownNow();
        }

        int[] afterRelease = housekeeping.run(Duration.ofNanos(1));

        assertThat(afterRelease[0]).isGreaterThanOrEqualTo(1);
        assertThat(statusOf(id)).isEqualTo("ENDED");
    }
}
