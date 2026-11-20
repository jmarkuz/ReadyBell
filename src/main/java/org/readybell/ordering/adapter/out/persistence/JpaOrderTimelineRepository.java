package org.readybell.ordering.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.readybell.ordering.domain.Order;
import org.readybell.ordering.domain.OrderItem;
import org.readybell.ordering.domain.OrderStatus;
import org.readybell.ordering.domain.OrderTimelineRepository;
import org.readybell.shared.domain.jpa.CrossTenant;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** PostgreSQL adapter of {@link OrderTimelineRepository}; plain inserts in the caller's transaction. */
@Repository
@Transactional
public class JpaOrderTimelineRepository implements OrderTimelineRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    @CrossTenant("the order carries its own tenant")
    public void recordTransition(Order order, OrderStatus from, Instant at) {
        em.createNativeQuery("""
                INSERT INTO order_status_history (order_id, tenant_id, from_status, to_status, at)
                VALUES (:order, :tenant, :from, :to, :at)
                """)
                .setParameter("order", order.getId().value())
                .setParameter("tenant", order.getTenantId().value())
                .setParameter("from", from == null ? null : from.name())
                .setParameter("to", order.getStatus().name())
                .setParameter("at", at)
                .executeUpdate();
    }

    @Override
    @CrossTenant("the order carries its own tenant")
    public void recordFinished(Order order, Instant at) {
        int quantity = order.getItems().stream().mapToInt(OrderItem::quantity).sum();
        em.createNativeQuery("""
                INSERT INTO order_fact (order_id, tenant_id, created_at, in_progress_at, ready_at, finished_at, outcome,
                                        item_count, quantity, connected)
                VALUES (:order, :tenant, :created,
                        (SELECT min(h.at) FROM order_status_history h WHERE h.order_id = :order AND h.to_status = 'IN_PROGRESS'),
                        (SELECT min(h.at) FROM order_status_history h WHERE h.order_id = :order AND h.to_status = 'READY'),
                        :finished, :outcome, :items, :quantity, :connected)
                ON CONFLICT (order_id) DO NOTHING
                """)
                .setParameter("order", order.getId().value())
                .setParameter("tenant", order.getTenantId().value())
                .setParameter("created", order.getCreatedAt())
                .setParameter("finished", at)
                .setParameter("outcome", order.getStatus().name())
                .setParameter("items", order.getItems().size())
                .setParameter("quantity", quantity)
                .setParameter("connected", order.isConnected())
                .executeUpdate();
    }
}
