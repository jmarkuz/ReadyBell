package org.readybell.integration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.readybell.notification.domain.NotificationSender;
import org.readybell.ordering.application.ManageOrderUseCase;
import org.readybell.ordering.application.TransitionOrderCommand;
import org.readybell.ordering.domain.OrderDetails;
import org.readybell.ordering.domain.OrderItem;
import org.readybell.ordering.domain.OrderStatus;
import org.readybell.platform.retention.RetentionService;
import org.readybell.shared.domain.TenantId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Personal data is removed from finished orders, old notification texts and ended sessions, while the facts that
 * analytics needs stay. Every run is scoped to the test's own tenant: the database is shared with other tests.
 */
@SpringBootTest
@ActiveProfiles("test")
class RetentionTest {

    @Autowired private RetentionService retention;
    @Autowired private ManageOrderUseCase orders;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @PersistenceContext private EntityManager em;
    @MockBean private NotificationSender sender;

    private final TenantId tenantId = new TenantId(UUID.randomUUID());
    private final UUID tenant = tenantId.value();
    private final Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    private final Instant old = now.minus(Duration.ofDays(40));
    private int number = 0;

    /** An order inserted directly, as if it had been created {@code created} ago; returns its id. */
    private UUID order(UUID tenant, String status, Instant updatedAt, String name, UUID session, String... items) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO "order" (id, tenant_id, session_id, status, customer_name, created_at, updated_at, version, order_number)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?)""",
                id, tenant, session, status, name, java.sql.Timestamp.from(updatedAt.minusSeconds(600)),
                java.sql.Timestamp.from(updatedAt), ++number + (int) (Math.random() * 100_000_000));
        int position = 0;
        for (String item : items) {
            jdbc.update("INSERT INTO order_item (\"position\", quantity, order_id, name) VALUES (?, 2, ?, ?)", position++, id, item);
        }
        return id;
    }

    private UUID session(UUID tenant, String status, Instant startedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO customer_session (id, tenant_id, status, started_at) VALUES (?, ?, ?, ?)",
                id, tenant, status, java.sql.Timestamp.from(startedAt));
        return id;
    }

    private Map<String, Object> row(UUID id) {
        return jdbc.queryForMap("SELECT customer_name, session_id, anonymized_at FROM \"order\" WHERE id = ?", id);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void anOldFinishedOrderLosesItsPersonalDataAndKeepsItsFact() {
        UUID session = session(tenant, "ENDED", old);
        UUID id = order(tenant, "COMPLETED", old, "Anna Petrenko", session, "Latte for Anna", "Croissant");

        Map<String, Integer> result = retention.run(now, tenant);

        assertThat(result.get("anonymize-orders")).isEqualTo(1);
        var after = row(id);
        assertThat(after.get("customer_name")).isNull();
        assertThat(after.get("session_id")).isNotEqualTo(session);          // the customer's link no longer reaches it
        assertThat(after.get("anonymized_at")).isNotNull();
        assertThat(count("SELECT count(*) FROM order_item WHERE order_id = ?", id)).isZero();
        var fact = jdbc.queryForMap("SELECT * FROM order_fact WHERE order_id = ?", id);
        assertThat(fact.get("outcome")).isEqualTo("COMPLETED");
        assertThat(fact.get("item_count")).isEqualTo(2);
        assertThat(fact.get("quantity")).isEqualTo(4);
        assertThat(fact.get("approximate")).isEqualTo(true);                // predates the fact table: timings estimated
        assertThat(fact.toString()).doesNotContain("Anna").doesNotContain("Latte");
    }

    @Test
    void recentOrdersActiveOrdersAndOtherTenantsAreLeftAlone() {
        UUID recent = order(tenant, "COMPLETED", now.minus(Duration.ofDays(5)), "Recent Customer", UUID.randomUUID(), "Tea");
        UUID active = order(tenant, "IN_PROGRESS", old, "Still Waiting", UUID.randomUUID(), "Soup");
        UUID foreign = order(UUID.randomUUID(), "COMPLETED", old, "Other Restaurant Customer", UUID.randomUUID(), "Pizza");

        retention.run(now, tenant);

        assertThat(row(recent).get("customer_name")).isEqualTo("Recent Customer");
        assertThat(row(active).get("customer_name")).isEqualTo("Still Waiting");
        assertThat(count("SELECT count(*) FROM order_item WHERE order_id = ?", active)).isEqualTo(1);
        assertThat(row(foreign).get("customer_name")).isEqualTo("Other Restaurant Customer");   // not this tenant's run
        assertThat(count("SELECT count(*) FROM order_fact WHERE order_id IN (?, ?, ?)", recent, active, foreign)).isZero();
    }

    @Test
    void aSecondRunChangesNothing() {
        order(tenant, "CANCELLED", old, "Anna", UUID.randomUUID(), "Latte");
        retention.run(now, tenant);

        Map<String, Integer> second = retention.run(now, tenant);

        assertThat(second.values()).allMatch(n -> n == 0);
    }

    @Test
    void anOrderFinishedThroughTheServiceHasRealTimingsAndKeepsThemWhenAnonymized() {
        var order = orders.createOrderWithNewSession(tenantId,
                new OrderDetails("Olena", List.of(new OrderItem("Borscht", 3)), null));
        orders.transitionOrderStatus(new TransitionOrderCommand(tenantId, order.getId(), OrderStatus.IN_PROGRESS));
        orders.transitionOrderStatus(new TransitionOrderCommand(tenantId, order.getId(), OrderStatus.READY));
        assertThat(count("SELECT count(*) FROM order_fact WHERE order_id = ?", order.getId().value())).isZero();   // still open
        orders.transitionOrderStatus(new TransitionOrderCommand(tenantId, order.getId(), OrderStatus.COMPLETED));

        assertThat(jdbc.queryForList("SELECT to_status FROM order_status_history WHERE order_id = ? ORDER BY at, id",
                String.class, order.getId().value()))
                .containsExactly("CREATED", "IN_PROGRESS", "READY", "COMPLETED");
        var fact = jdbc.queryForMap("SELECT * FROM order_fact WHERE order_id = ?", order.getId().value());
        assertThat(fact.get("approximate")).isEqualTo(false);
        assertThat(fact.get("in_progress_at")).isNotNull();
        assertThat(fact.get("ready_at")).isNotNull();
        assertThat(fact.get("quantity")).isEqualTo(3);

        retention.run(now.plus(Duration.ofDays(31)), tenant);                // a month later

        assertThat(row(order.getId().value()).get("customer_name")).isNull();
        var kept = jdbc.queryForMap("SELECT * FROM order_fact WHERE order_id = ?", order.getId().value());
        assertThat(kept.get("ready_at")).isEqualTo(fact.get("ready_at"));    // the real timings survive the anonymization
        assertThat(kept.get("approximate")).isEqualTo(false);
    }

    @Test
    void aTransitionThatFailsLeavesNoHistoryBehind() {
        var order = orders.createOrderWithNewSession(tenantId, OrderDetails.NONE);

        assertThatThrownBy(() -> orders.transitionOrderStatus(
                new TransitionOrderCommand(tenantId, order.getId(), OrderStatus.COMPLETED)))      // CREATED cannot jump to COMPLETED
                .isInstanceOf(RuntimeException.class);

        assertThat(count("SELECT count(*) FROM order_status_history WHERE order_id = ?", order.getId().value())).isEqualTo(1);
    }

    @Test
    void oldNotificationTextsAreEmptiedButPendingOnesAreNot() {
        UUID sent = notification("SENT", old, "Order #12 is ready for pickup!");
        UUID pending = notification("PENDING", old, "Order #13 is ready for pickup!");
        UUID recent = notification("SENT", now.minus(Duration.ofDays(2)), "Order #14 is ready for pickup!");

        retention.run(now, tenant);

        assertThat(jdbc.queryForObject("SELECT payload FROM notification WHERE id = ?", String.class, sent)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT status FROM notification WHERE id = ?", String.class, sent)).isEqualTo("SENT");
        assertThat(jdbc.queryForObject("SELECT payload FROM notification WHERE id = ?", String.class, pending)).contains("#13");
        assertThat(jdbc.queryForObject("SELECT payload FROM notification WHERE id = ?", String.class, recent)).contains("#14");
    }

    private UUID notification(String status, Instant createdAt, String text) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO notification (id, tenant_id, subscription_id, status, payload, created_at, attempts, title, url)
                VALUES (?, ?, ?, ?, ?, ?, 0, 'Cafe', '/track/x')""",
                id, tenant, UUID.randomUUID(), status, text, java.sql.Timestamp.from(createdAt));
        return id;
    }

    @Test
    void endedSessionsAreDeletedOnceNoOrderReferToThem() {
        UUID free = session(tenant, "ENDED", old);
        UUID withRecentOrder = session(tenant, "ENDED", old);
        order(tenant, "COMPLETED", now.minus(Duration.ofDays(3)), "Kept", withRecentOrder);
        UUID withOldOrder = session(tenant, "ENDED", old);
        order(tenant, "COMPLETED", old, "Forgotten", withOldOrder);
        UUID active = session(tenant, "ACTIVE", old);

        retention.run(now, tenant);

        assertThat(count("SELECT count(*) FROM customer_session WHERE id = ?", free)).isZero();
        assertThat(count("SELECT count(*) FROM customer_session WHERE id = ?", withRecentOrder)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM customer_session WHERE id = ?", active)).isEqualTo(1);
        // the old order was anonymized in the same run (its session id became random), so its old session is free for the
        // next one
        retention.run(now, tenant);
        assertThat(count("SELECT count(*) FROM customer_session WHERE id = ?", withOldOrder)).isZero();
    }

    @Test
    void aDryRunReportsTheWorkAndChangesNothing() {
        UUID id = order(tenant, "COMPLETED", old, "Anna", UUID.randomUUID(), "Latte");
        var dry = new RetentionService(transactionManager, Clock.systemUTC(), new SimpleMeterRegistry(),
                true, true, 1, 30, 30, 30);
        ReflectionTestUtils.setField(dry, "em", em);

        Map<String, Integer> result = dry.run(now, tenant);

        assertThat(result.get("backfill-facts")).isEqualTo(1);
        assertThat(result.get("anonymize-orders")).isZero();               // no fact row exists yet, and none was written
        assertThat(row(id).get("customer_name")).isEqualTo("Anna");
        assertThat(count("SELECT count(*) FROM order_fact WHERE order_id = ?", id)).isZero();
        assertThat(count("SELECT count(*) FROM retention_run WHERE dry_run = true")).isPositive();
    }

    @Test
    void manyOrdersAreHandledInBatches() {
        var small = new RetentionService(transactionManager, Clock.systemUTC(), new SimpleMeterRegistry(),
                true, false, 2, 30, 30, 30);
        ReflectionTestUtils.setField(small, "em", em);
        for (int i = 0; i < 5; i++) {
            order(tenant, "COMPLETED", old, "Customer " + i, UUID.randomUUID(), "Item " + i);
        }

        Map<String, Integer> result = small.run(now, tenant);

        assertThat(result.get("anonymize-orders")).isEqualTo(5);
        assertThat(count("SELECT count(*) FROM \"order\" WHERE tenant_id = ? AND customer_name IS NOT NULL", tenant)).isZero();
    }

    @Test
    void everyRunIsRecordedForTheOperator() {
        order(tenant, "COMPLETED", old, "Anna", UUID.randomUUID());
        int before = count("SELECT count(*) FROM retention_run WHERE task = 'anonymize-orders' AND error IS NULL");

        retention.run(now, tenant);

        assertThat(count("SELECT count(*) FROM retention_run WHERE task = 'anonymize-orders' AND error IS NULL"))
                .isEqualTo(before + 1);
        assertThat(count("SELECT count(*) FROM retention_run WHERE finished_at IS NULL")).isZero();
    }

    @Test
    void theRetentionPeriodsHaveLimits() {
        assertThatThrownBy(() -> new RetentionService(transactionManager, Clock.systemUTC(), new SimpleMeterRegistry(),
                true, false, 500, 3, 30, 30)).hasMessageContaining("order-days");
        assertThatThrownBy(() -> new RetentionService(transactionManager, Clock.systemUTC(), new SimpleMeterRegistry(),
                true, false, 500, 30, 30, 1000)).hasMessageContaining("session-days");
    }
}
