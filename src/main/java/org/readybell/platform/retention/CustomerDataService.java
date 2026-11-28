package org.readybell.platform.retention;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What a customer may do with their own data, with no account: the link to their order (the session id) is the proof that
 * it is theirs, as for the tracking page.
 *
 * <ul>
 *   <li>{@link #export}: everything held about the session, as a machine-readable document (access and portability);</li>
 *   <li>{@link #erase}: push subscriptions deleted, the customer name of every order removed, finished orders
 *       anonymized at once (name, items, link to the session; the fact row stays). An order still being prepared keeps
 *       its items for the kitchen, loses the name, and is anonymized by the retention run once it is finished.</li>
 * </ul>
 * Each erasure is recorded in {@code erasure_log} under a keyed hash of the session id.
 */
@Service
public class CustomerDataService {
    private final RetentionService retention;
    private final Clock clock;
    private final byte[] logKey;

    @PersistenceContext
    private EntityManager em;

    public CustomerDataService(RetentionService retention, Clock clock,
                               @Value("${readybell.security.jwt.secret:}") String secret) {
        this.retention = retention;
        this.clock = clock;
        this.logKey = ("erasure-log:" + secret).getBytes(StandardCharsets.UTF_8);
    }

    /** What an erasure did, for the customer. */
    public record Erased(int subscriptions, int ordersAnonymized, int ordersKeptUntilFinished) {}

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Map<String, Object> export(UUID session) {
        if (tenantOf(session) == null) {
            return null;
        }
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("session", session.toString());
        doc.put("generatedAt", clock.instant().toString());
        doc.put("about", "Everything ReadyBell holds about this link. Orders older than the retention period no longer carry "
                + "your name or items.");

        List<Map<String, Object>> orders = new ArrayList<>();
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
                SELECT id, order_number, status, customer_name, created_at, updated_at, connected_at, anonymized_at
                FROM "order" WHERE session_id = :s ORDER BY created_at""").setParameter("s", session).getResultList()) {
            Map<String, Object> order = new LinkedHashMap<>();
            order.put("orderNumber", "#" + r[1]);
            order.put("status", r[2]);
            order.put("customerName", r[3]);
            order.put("createdAt", instant(r[4]));
            order.put("updatedAt", instant(r[5]));
            order.put("openedOnPhoneAt", instant(r[6]));
            order.put("anonymized", r[7] != null);
            List<Map<String, Object>> items = new ArrayList<>();
            for (Object[] i : (List<Object[]>) em.createNativeQuery(
                    "SELECT name, quantity FROM order_item WHERE order_id = :id ORDER BY \"position\"")
                    .setParameter("id", r[0]).getResultList()) {
                items.add(Map.of("name", i[0], "quantity", i[1]));
            }
            order.put("items", items);
            orders.add(order);
        }
        doc.put("orders", orders);

        List<Map<String, Object>> devices = new ArrayList<>();
        for (Object[] d : (List<Object[]>) em.createNativeQuery(
                "SELECT endpoint_url, consent_at, consent_notice FROM device_subscription WHERE session_id = :s")
                .setParameter("s", session).getResultList()) {
            Map<String, Object> device = new LinkedHashMap<>();
            device.put("pushService", host((String) d[0]));            // the address of the device itself is not handed out
            device.put("agreedAt", instant(d[1]));
            device.put("noticeVersion", d[2]);
            devices.add(device);
        }
        doc.put("pushSubscriptions", devices);
        return doc;
    }

    @Transactional
    public Erased erase(UUID session) {
        UUID tenant = tenantOf(session);
        if (tenant == null) {
            return null;
        }
        Instant now = clock.instant();
        Erased erased = apply(session, now);
        em.createNativeQuery("""
                INSERT INTO erasure_log (id, tenant_id, subject_type, subject_ref, scope, performed_at, performed_by)
                VALUES (:id, :tenant, 'CUSTOMER_SESSION', :ref, :scope, :at, 'CUSTOMER')""")
                .setParameter("id", UUID.randomUUID()).setParameter("tenant", tenant).setParameter("ref", subjectRef(session))
                .setParameter("scope", "subscriptions,orders").setParameter("at", now).executeUpdate();
        return erased;
    }

    /**
     * After a restore from a backup: forgets again what the log says was forgotten. The log holds keyed hashes, so each
     * session of the restored database is hashed and looked up; nothing is logged again.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public int replayErasures() {
        java.util.Set<String> refs = new java.util.HashSet<>((List<String>) em.createNativeQuery(
                "SELECT subject_ref FROM erasure_log WHERE subject_type = 'CUSTOMER_SESSION'").getResultList());
        if (refs.isEmpty()) {
            return 0;
        }
        Instant now = clock.instant();
        int replayed = 0;
        UUID after = new UUID(0, 0);
        while (true) {
            List<Object> ids = em.createNativeQuery("SELECT id FROM customer_session WHERE id > :after ORDER BY id LIMIT 1000")
                    .setParameter("after", after).getResultList();
            if (ids.isEmpty()) {
                return replayed;
            }
            for (Object o : ids) {
                UUID session = uuid(o);
                after = session;
                if (refs.contains(subjectRef(session))) {
                    Erased e = apply(session, now);
                    if (e.subscriptions() + e.ordersAnonymized() + e.ordersKeptUntilFinished() > 0) {
                        replayed++;
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Erased apply(UUID session, Instant now) {
        List<UUID> subscriptionIds = ((List<Object>) em.createNativeQuery(
                "SELECT id FROM device_subscription WHERE session_id = :s").setParameter("s", session).getResultList())
                .stream().map(CustomerDataService::uuid).toList();
        if (!subscriptionIds.isEmpty()) {
            // the text of what was pushed to those devices goes with them
            em.createNativeQuery("UPDATE notification SET payload = '', title = NULL, url = NULL WHERE subscription_id IN (:ids)")
                    .setParameter("ids", subscriptionIds).executeUpdate();
            em.createNativeQuery("DELETE FROM device_subscription WHERE session_id = :s").setParameter("s", session).executeUpdate();
        }

        List<UUID> finished = ((List<Object>) em.createNativeQuery("""
                SELECT id FROM "order" WHERE session_id = :s AND status IN ('COMPLETED', 'CANCELLED') AND anonymized_at IS NULL
                FOR UPDATE""").setParameter("s", session).getResultList()).stream().map(CustomerDataService::uuid).toList();
        int anonymized = 0;
        if (!finished.isEmpty()) {
            retention.writeFacts(finished);                       // the history the business keeps is written first
            anonymized = retention.anonymize(finished, now);
        }
        int kept = em.createNativeQuery("""
                UPDATE "order" SET customer_name = NULL
                WHERE session_id = :s AND status NOT IN ('COMPLETED', 'CANCELLED') AND customer_name IS NOT NULL""")
                .setParameter("s", session).executeUpdate();
        return new Erased(subscriptionIds.size(), anonymized, kept);
    }

    /** Keyed hash of the session id: enough to find the entry again, useless to anyone without the key. */
    String subjectRef(UUID session) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(logKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(("session:" + session).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private UUID tenantOf(UUID session) {
        @SuppressWarnings("unchecked")
        List<Object> rows = em.createNativeQuery("SELECT tenant_id FROM customer_session WHERE id = :s")
                .setParameter("s", session).getResultList();
        return rows.isEmpty() ? null : uuid(rows.get(0));
    }

    private static UUID uuid(Object o) {
        return o instanceof UUID u ? u : UUID.fromString(o.toString());
    }

    private static String instant(Object o) {
        if (o == null) {
            return null;
        }
        return o instanceof java.sql.Timestamp t ? t.toInstant().toString() : o.toString();
    }

    private static String host(String endpoint) {
        try {
            return URI.create(endpoint).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
