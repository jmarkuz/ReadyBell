package org.readybell.platform.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs {@code V4__sequential_order_numbers.sql} on PostgreSQL against a database that already holds orders (the V1-V3
 * shape) and checks the backfill: oldest first per restaurant, no duplicates, counters seeded so new orders continue
 * the sequence.
 */
@Testcontainers
class OrderNumberMigrationTest {

    private static final String A = "00000000-0000-0000-0000-00000000000a";
    private static final String B = "00000000-0000-0000-0000-00000000000b";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private Flyway flyway;

    @BeforeEach
    void freshSchema() {
        flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .cleanDisabled(false)
                .load();
        flyway.clean();
    }

    @Test
    void backfillsExistingOrdersByCreatedAtPerRestaurantAndSeedsCounters() throws Exception {
        migrateTo("3");
        try (Connection c = connection()) {
            // restaurant A: inserted out of chronological order; restaurant B: two orders with the same created_at
            insert(c, "00000000-0000-0000-0000-000000000003", A, "2026-01-03 10:00:00+00");
            insert(c, "00000000-0000-0000-0000-000000000001", A, "2026-01-01 10:00:00+00");
            insert(c, "00000000-0000-0000-0000-000000000002", A, "2026-01-02 10:00:00+00");
            insert(c, "00000000-0000-0000-0000-000000000011", B, "2026-02-01 10:00:00+00");
            insert(c, "00000000-0000-0000-0000-000000000010", B, "2026-02-01 10:00:00+00");

            migrateTo("4");

            assertThat(numbersFor(c, A)).containsExactly(1, 2, 3);          // by created_at, not insertion order
            assertThat(numbersFor(c, B)).containsExactly(1, 2);             // tie broken deterministically by id
            assertThat(query(c, "SELECT order_number FROM \"order\" WHERE id = '00000000-0000-0000-0000-000000000001'"))
                    .containsExactly(1);
            assertThat(query(c, "SELECT last_number FROM order_counter WHERE tenant_id = '" + A + "'")).containsExactly(3);
            assertThat(query(c, "SELECT last_number FROM order_counter WHERE tenant_id = '" + B + "'")).containsExactly(2);
            assertThat(query(c, "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'order_number_backfill'"))
                    .containsExactly(0);

            // the sequence is unique per restaurant and the column is mandatory
            try (Statement s = c.createStatement()) {
                assertThatThrownBy(() -> s.execute(insertSql(UUID.randomUUID().toString(), A, "now()", ", order_number", ", 2")))
                        .isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> s.execute(insertSql(UUID.randomUUID().toString(), A, "now()", "", "")))
                        .isInstanceOf(SQLException.class);
            }
        }
    }

    @Test
    void migratesAnEmptyDatabase() throws Exception {
        migrateTo("3");
        migrateTo("4");
        try (Connection c = connection()) {
            assertThat(query(c, "SELECT COUNT(*) FROM order_counter")).containsExactly(0);
        }
    }

    private void migrateTo(String version) {
        Flyway.configure()
                .configuration(flyway.getConfiguration())
                .target(version)
                .load()
                .migrate();
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void insert(Connection c, String id, String tenant, String createdAt) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute(insertSql(id, tenant, "'" + createdAt + "'", "", ""));
        }
    }

    /** An order row as it looked before V4 (all V1 NOT NULL columns); the extra column list lets a test add order_number. */
    private static String insertSql(String id, String tenant, String createdAt, String extraColumn, String extraValue) {
        return "INSERT INTO \"order\" (id, tenant_id, session_id, status, version, created_at, updated_at" + extraColumn + ") "
                + "VALUES ('" + id + "', '" + tenant + "', '" + UUID.randomUUID() + "', 'CREATED', 0, "
                + createdAt + ", " + createdAt + extraValue + ")";
    }

    private static List<Integer> numbersFor(Connection c, String tenant) throws SQLException {
        return query(c, "SELECT order_number FROM \"order\" WHERE tenant_id = '" + tenant + "' ORDER BY created_at, id");
    }

    private static List<Integer> query(Connection c, String sql) throws SQLException {
        var out = new ArrayList<Integer>();
        try (Statement s = c.createStatement(); var rs = s.executeQuery(sql)) {
            while (rs.next()) {
                out.add(rs.getInt(1));
            }
        }
        return out;
    }
}
