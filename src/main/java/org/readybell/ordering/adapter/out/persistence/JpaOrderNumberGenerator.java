package org.readybell.ordering.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.readybell.ordering.domain.OrderNumberGenerator;
import org.readybell.shared.domain.TenantId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.UUID;

/**
 * Allocates per-restaurant order numbers from the {@code order_counter} row.
 *
 * <p>The {@code UPDATE ... + 1} takes a row lock that is held until the surrounding transaction (the one that also
 * inserts the order) ends, so concurrent creations for the same restaurant are serialised, numbers are consecutive
 * and a rollback gives its number back. Counters of different restaurants never contend.
 *
 * <p>Plain JDBC with only portable SQL.
 */
@Repository
@Transactional
public class JpaOrderNumberGenerator implements OrderNumberGenerator {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public int next(TenantId tenantId) {
        UUID tenant = tenantId.value();
        return entityManager.unwrap(Session.class).doReturningWork(connection -> {
            ensureCounter(connection, tenant);
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE order_counter SET last_number = last_number + 1 WHERE tenant_id = ?")) {
                update.setObject(1, tenant);
                update.executeUpdate();
            }
            // Read back inside the same transaction: our row lock keeps the value stable
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT last_number FROM order_counter WHERE tenant_id = ?")) {
                select.setObject(1, tenant);
                try (ResultSet rs = select.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
    }

    /**
     * Creates the restaurant's counter on first use. Two first-ever creations can race on the insert; the loser's
     * duplicate-key error is rolled back to a savepoint (a plain failed statement would poison the PostgreSQL
     * transaction) and it carries on to the locking UPDATE.
     */
    private static void ensureCounter(Connection connection, UUID tenant) throws SQLException {
        try (PreparedStatement exists = connection.prepareStatement(
                "SELECT 1 FROM order_counter WHERE tenant_id = ?")) {
            exists.setObject(1, tenant);
            try (ResultSet rs = exists.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }
        }
        Savepoint savepoint = connection.setSavepoint();
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO order_counter (tenant_id, last_number) VALUES (?, 0)")) {
            insert.setObject(1, tenant);
            insert.executeUpdate();
        } catch (SQLException duplicate) {
            connection.rollback(savepoint);   // someone else created it first
        } finally {
            try {
                connection.releaseSavepoint(savepoint);
            } catch (SQLException ignored) {
                // already gone after the rollback on some drivers
            }
        }
    }
}
