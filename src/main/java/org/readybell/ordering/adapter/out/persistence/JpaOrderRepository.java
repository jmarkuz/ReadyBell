package org.readybell.ordering.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.hibernate.Session;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaExpression;
import org.readybell.ordering.domain.Order;
import org.readybell.ordering.domain.OrderId;
import org.readybell.ordering.domain.OrderPage;
import org.readybell.ordering.domain.OrderQuery;
import org.readybell.ordering.domain.OrderRepository;
import org.readybell.ordering.domain.OrderStatus;
import org.readybell.ordering.domain.OrderStatusCounts;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.TenantId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * JPA adapter implementing the {@link OrderRepository} port.
 *
 * <h3>Tenant filter activation (Req 10.2, 10.4)</h3>
 * <p>The Hibernate {@code tenantFilter} is enabled per query before any
 * {@code SELECT} is executed, constraining results to the current tenant
 * and excluding cross-tenant rows (Req 10.3).
 *
 * <h3>Optimistic locking (Req 5.8)</h3>
 * <p>The {@code @Version} column on {@link OrderJpaEntity} ensures JPA raises an
 * {@code OptimisticLockException} when two concurrent transitions try to update the
 * same row, guaranteeing at most one succeeds.
 */
@Repository
@Transactional
public class JpaOrderRepository implements OrderRepository {

    private final OrderJpaRepository springRepo;

    @PersistenceContext
    private EntityManager entityManager;

    public JpaOrderRepository(OrderJpaRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public void save(Order order) {
        var entity = OrderJpaEntity.from(order);
        springRepo.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(OrderId id) {
        return springRepo.findById(id.value())
                .map(OrderJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByTenantAndId(TenantId tenantId, OrderId id) {
        enableTenantFilter(tenantId);
        return springRepo.findByIdAndTenantId(id.value(), tenantId.value())
                .map(OrderJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByTenantAndSession(TenantId tenantId, CustomerSessionId sessionId) {
        enableTenantFilter(tenantId);
        return springRepo.findByTenantIdAndSessionId(tenantId.value(), sessionId.value())
                .stream()
                .map(OrderJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findAllByTenant(TenantId tenantId) {
        enableTenantFilter(tenantId);
        return springRepo.findByTenantId(tenantId.value())
                .stream()
                .map(OrderJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderPage findPage(TenantId tenantId, OrderQuery query) {
        enableTenantFilter(tenantId);
        Specification<OrderJpaEntity> spec = (root, cq, cb) -> {
            List<Predicate> and = new ArrayList<>();
            and.add(cb.equal(root.get("tenantId"), tenantId.value()));
            if (!query.statuses().isEmpty()) {
                and.add(root.get("status").in(query.statuses()));
            }
            if (query.search() != null) {
                and.add(searchPredicate(root, cq, cb, query.search()));
            }
            if (query.from() != null) {
                and.add(cb.greaterThanOrEqualTo(root.get("createdAt"), query.from()));
            }
            if (query.to() != null) {
                and.add(cb.lessThanOrEqualTo(root.get("createdAt"), query.to()));
            }
            return cb.and(and.toArray(Predicate[]::new));
        };
        Sort.Direction dir = query.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(dir, sortProperty(query.sortBy()));
        if (!"id".equals(sortProperty(query.sortBy()))) {
            sort = sort.and(Sort.by(Sort.Direction.ASC, "id")); // stable paging
        }
        var page = springRepo.findAll(spec, PageRequest.of(query.page() - 1, query.pageSize(), sort));
        return new OrderPage(page.getContent().stream().map(OrderJpaEntity::toDomain).toList(),
                page.getTotalElements(), query.page(), query.pageSize());
    }

    @Override
    @Transactional(readOnly = true)
    public OrderStatusCounts countByStatus(TenantId tenantId) {
        enableTenantFilter(tenantId);
        var counts = new EnumMap<OrderStatus, Long>(OrderStatus.class);
        for (Object[] row : springRepo.countGroupedByStatus(tenantId.value())) {
            counts.put((OrderStatus) row[0], (Long) row[1]);
        }
        return new OrderStatusCounts(counts);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findUpdatedSince(TenantId tenantId, Instant since, int limit) {
        enableTenantFilter(tenantId);
        Specification<OrderJpaEntity> spec = (root, cq, cb) -> since == null
                ? cb.equal(root.get("tenantId"), tenantId.value())
                : cb.and(cb.equal(root.get("tenantId"), tenantId.value()),
                         cb.greaterThanOrEqualTo(root.get("updatedAt"), since));
        var page = springRepo.findAll(spec,
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by("id"))));
        return page.getContent().stream().map(OrderJpaEntity::toDomain).toList();
    }

    private static String sortProperty(OrderQuery.Sort sort) {
        return switch (sort) {
            case CREATED -> "createdAt";
            case ETA -> "estimatedReadyAt";
            case STATUS -> "status";
            case CUSTOMER_NAME -> "customerName";
            case ORDER_ID -> "id";
        };
    }

    /** Matches the order number prefix, the customer name, or any item name (case-insensitive, literal text). */
    private static Predicate searchPredicate(Root<OrderJpaEntity> root, CriteriaQuery<?> cq,
                                             CriteriaBuilder cb, String search) {
        String term = search.toLowerCase(Locale.ROOT);
        String idPrefix = term.startsWith("#") ? term.substring(1) : term;
        String contains = "%" + escapeLike(term) + "%";

        Subquery<Integer> items = cq.subquery(Integer.class);
        Root<OrderJpaEntity> itemRoot = items.from(OrderJpaEntity.class);
        Join<OrderJpaEntity, OrderItemEmbeddable> item = itemRoot.join("items");
        items.select(cb.literal(1)).where(
                cb.equal(itemRoot.get("id"), root.get("id")),
                cb.like(cb.lower(item.get("name")), contains, '\\'));

        return cb.or(
                // Real SQL CAST: Expression.as() only reinterprets the Java type, and PostgreSQL has no lower(uuid)
                cb.like(cb.lower(((HibernateCriteriaBuilder) cb).cast((JpaExpression<?>) root.get("id"), String.class)),
                        escapeLike(idPrefix) + "%", '\\'),
                cb.like(cb.lower(root.get("customerName")), contains, '\\'),
                cb.exists(items));
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    // -------------------------------------------------------------------------
    // Tenant filter helper
    // -------------------------------------------------------------------------

    private void enableTenantFilter(TenantId tenantId) {
        entityManager.unwrap(Session.class)
                .enableFilter("tenantFilter")
                .setParameter("tenantId", tenantId.value());
    }
}
