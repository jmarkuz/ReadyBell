package org.readybell.platform.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.file.Path;
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
 * Runs {@code V4__sequential_order_numbers.sql} against a database that already holds orders (pre-V4 shape) and
 * checks the backfill: oldest first per restaurant, no duplicates, counters seeded so new orders continue the sequence.
 */
class OrderNumberMigrationTest {

    private static final String A = "00000000-0000-0000-0000-00000000000a";
    private static final String B = "00000000-0000-0000-0000-00000000000b";

    @Test
    void backfillsExistingOrdersByCreatedAtPerRestaurantAndSeedsCounters() throws Exception {
        String url = "jdbc:h2:mem:migration" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        try (Connection c = DriverManager.getConnection(url)) {
            try (Statement s = c.createStatement()) {
                s.execute("CREATE TABLE \"order\" (id uuid NOT NULL PRIMARY KEY, tenant_id uuid NOT NULL, "
                        + "created_at timestamp with time zone NOT NULL)");
                // restaurant A: inserted out of chronological order; restaurant B: one order with a created_at tie
                insert(s, "00000000-0000-0000-0000-000000000003", A, "2026-01-03 10:00:00+00");
                insert(s, "00000000-0000-0000-0000-000000000001", A, "2026-01-01 10:00:00+00");
                insert(s, "00000000-0000-0000-0000-000000000002", A, "2026-01-02 10:00:00+00");
                insert(s, "00000000-0000-0000-0000-000000000011", B, "2026-02-01 10:00:00+00");
                insert(s, "00000000-0000-0000-0000-000000000010", B, "2026-02-01 10:00:00+00");
            }

            ScriptUtils.executeSqlScript(c, new FileSystemResource(
                    Path.of("src/main/resources/db/migration/V4__sequential_order_numbers.sql")));

            assertThat(numbersFor(c, A)).containsExactly(1, 2, 3);          // by created_at, not insertion order
            assertThat(numbersFor(c, B)).containsExactly(1, 2);             // tie broken deterministically by id
            assertThat(query(c, "SELECT order_number FROM \"order\" WHERE id = '00000000-0000-0000-0000-000000000001'"))
                    .containsExactly(1);
            assertThat(query(c, "SELECT last_number FROM order_counter WHERE tenant_id = '" + A + "'")).containsExactly(3);
            assertThat(query(c, "SELECT last_number FROM order_counter WHERE tenant_id = '" + B + "'")).containsExactly(2);
            assertThat(query(c, "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'ORDER_NUMBER_BACKFILL'"))
                    .containsExactly(0);

            // the sequence is unique per restaurant and the column is mandatory
            try (Statement s = c.createStatement()) {
                assertThatThrownBy(() -> s.execute("INSERT INTO \"order\" (id, tenant_id, created_at, order_number) VALUES ('"
                        + UUID.randomUUID() + "', '" + A + "', CURRENT_TIMESTAMP, 2)")).isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> s.execute("INSERT INTO \"order\" (id, tenant_id, created_at) VALUES ('"
                        + UUID.randomUUID() + "', '" + A + "', CURRENT_TIMESTAMP)")).isInstanceOf(SQLException.class);
            }
        }
    }

    @Test
    void migratesAnEmptyDatabase() throws Exception {
        String url = "jdbc:h2:mem:migration" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        try (Connection c = DriverManager.getConnection(url)) {
            try (Statement s = c.createStatement()) {
                s.execute("CREATE TABLE \"order\" (id uuid NOT NULL PRIMARY KEY, tenant_id uuid NOT NULL, "
                        + "created_at timestamp with time zone NOT NULL)");
            }
            ScriptUtils.executeSqlScript(c, new FileSystemResource(
                    Path.of("src/main/resources/db/migration/V4__sequential_order_numbers.sql")));
            assertThat(query(c, "SELECT COUNT(*) FROM order_counter")).containsExactly(0);
        }
    }

    private static void insert(Statement s, String id, String tenant, String createdAt) throws SQLException {
        s.execute("INSERT INTO \"order\" (id, tenant_id, created_at) VALUES ('" + id + "', '" + tenant + "', '" + createdAt + "')");
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
