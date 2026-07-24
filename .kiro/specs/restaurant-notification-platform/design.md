# Design Document

## Overview

ReadyBell is a multi-tenant, event-driven **modular monolith** built domain-first with
Domain-Driven Design (DDD) and Clean/Hexagonal (Ports & Adapters) architecture. Customers
scan a QR code, open a PWA, and receive Web Push notifications when their order is ready.
Restaurant staff and admins operate a Restaurant Panel, and a platform admin manages tenants.

The design begins from the **domain model** — aggregates, invariants, and domain events —
and only then defines the ports (interfaces) the domain needs and the adapters (Spring/JPA/
Redis/Web Push) that implement them. Domain events are published **in-process** behind a
`DomainEventPublisher` port so the transport can be swapped for an external broker later
without touching the domain. Persistence uses a **shared PostgreSQL schema** with a
`tenant_id` discriminator enforced at the repository/query layer. The application runs as
**stateless replicas** with session/cache state externalized to Redis, making it
Kubernetes-ready and horizontally scalable.

### Design Goals

- Domain purity: the domain layer has zero dependencies on Spring, JPA, or any adapter.
- Broker-swappable eventing behind ports (in-process now, external broker later).
- Strict tenant isolation enforced structurally at the query layer, not by convention.
- Sub-2-second order-ready notification latency measured from `OrderReady` to sender accept.
- Horizontal scalability via stateless replicas + externalized state.
- API-first: contracts defined before implementation.

### Technology Stack

Because the project currently has only the Gradle `java` plugin + JUnit, this design
introduces the following (see ADR-008):

| Concern | Choice |
| --- | --- |
| Language / runtime | Java 21 (LTS) |
| Application framework | Spring Boot 3.x (Web, Validation, Security) |
| Persistence | Spring Data JPA + Hibernate, PostgreSQL |
| In-process events | Spring `ApplicationEventPublisher` (behind a port) |
| Cache / session store | Redis (Spring Data Redis) |
| Web Push | VAPID via a Web Push library, behind `NotificationSender` port |
| Property-based testing | jqwik + JUnit 5 |
| Packaging | Docker image, Kubernetes deployment (≥2 replicas) |
| Frontends | Customer PWA + Restaurant Panel web app (separate SPA builds) |

Message broker is intentionally **deferred**; events stay in-process behind the port.

## Architecture

### Architectural Styles Applied

- **Domain-Driven Design** — the model is organized into bounded contexts, each with its
  own aggregates, invariants, and ubiquitous language drawn from the requirements glossary.
- **Clean / Hexagonal Architecture** — dependencies point inward. The domain defines ports;
  application services orchestrate use cases; adapters implement inbound (REST) and outbound
  (persistence, events, push) ports.
- **Event-Driven** — state changes emit domain events published through a port.
- **Modular Monolith** — one deployable, but internal module boundaries mirror bounded
  contexts so extraction into microservices is a refactor, not a rewrite.

### Dependency Rule (Clean Architecture)

```
            inbound adapters                    outbound adapters
        (REST controllers, PWA API)      (JPA repos, Redis, Web Push, Spring events)
                    |                                     ^
                    v                                     |
              Application Layer  ---- depends on ports --->|
             (use-case services)                          |
                    |                                     |
                    v                                     |
                 Domain Layer  ---- DEFINES ports ---------+
        (aggregates, value objects, domain events, port interfaces)
```

Rule: **domain depends on nothing; application depends on domain; adapters depend on
application + domain.** Nothing inner depends on anything outer. Ports live in the domain/
application layers; adapters live outside and are wired by Spring configuration.

### Module / Package Structure (Modular Monolith)

Each bounded context is a top-level module package with the same internal hexagonal shape.
The shared kernel holds only cross-context primitives (`TenantId`, `DomainEvent`, base types).

```
org.readybell
├── shared                     # shared kernel: TenantId, DomainEvent, base value objects
│   ├── domain
│   └── tenancy                # TenantContext holder + Tenant_Context_Filter support
├── session                    # Session bounded context
│   ├── domain                 # CustomerSession aggregate, events, ports
│   ├── application            # SessionService (use cases)
│   └── adapter
│       ├── in.web             # Customer Session REST API
│       └── out.persistence    # JPA repository adapter
├── ordering                   # Ordering bounded context
│   ├── domain                 # Order aggregate, Order_Lifecycle, events, ports
│   ├── application
│   └── adapter (in.web, out.persistence)
├── notification               # Notification bounded context
│   ├── domain                 # Notification, NotificationSender port
│   ├── application            # OrderReady event handler
│   └── adapter
│       ├── out.push           # VAPID Web Push adapter (+ fake adapter for tests)
│       └── out.persistence
├── subscription               # Subscription bounded context
│   ├── domain                 # DeviceSubscription aggregate, ports
│   ├── application
│   └── adapter (in.web, out.persistence)
├── restaurant                 # Restaurant/Tenant management + staff/admin
│   ├── domain                 # Restaurant, StaffAccount aggregates
│   ├── application
│   └── adapter (in.web, out.persistence)
├── platformadmin              # Platform admin (cross-tenant tenant management)
│   ├── application
│   └── adapter.in.web
└── platform                   # composition root: Spring config, event publisher adapter,
                               # security, tenant filter wiring
```

## Bounded Context Map

Six bounded contexts. Integration between contexts happens **only** through domain events
(published via the port) or through published application service interfaces — never by
reaching into another context's aggregates or tables directly.

```
                         ┌─────────────────────────┐
                         │   Restaurant / Tenant    │
                         │   (Restaurant, Staff,    │
                         │    Admin, config)        │
                         └───────────┬─────────────┘
             registers tenants ▲     │ provides Tenant_Id + staff identity
                               │     ▼
        ┌──────────────┐   validates    ┌──────────────────────────────┐
        │ Platform     │──── tenant ────▶│         Session               │
        │ Admin        │   existence     │  (CustomerSession aggregate)  │
        └──────────────┘                 └──────────┬───────────────────┘
                                CustomerSessionStarted│  provides session identity
                                                      ▼
                                        ┌──────────────────────────────┐
        ┌──────────────────────────┐    │         Ordering              │
        │      Subscription         │   │   (Order aggregate,           │
        │  (DeviceSubscription)     │    │    Order_Lifecycle)           │
        └──────────┬────────────────┘    └──────────┬───────────────────┘
                   │ queried for target subs        │ OrderCreated,
                   │                        OrderStatusUpdated, OrderReady
                   ▼                                 ▼
                       ┌───────────────────────────────────────┐
                       │            Notification                 │
                       │  (Notification, NotificationSender port)│
                       │  emits NotificationSent / Delivered     │
                       └───────────────────────────────────────┘
```

Context relationships (DDD patterns):

| Upstream | Downstream | Relationship |
| --- | --- | --- |
| Restaurant/Tenant | Session, Ordering | Customer/Supplier — tenant existence & staff identity |
| Session | Ordering, Subscription, Notification | Customer/Supplier — provides `CustomerSessionId` + `TenantId` |
| Ordering | Notification | Published Language — `OrderReady` event triggers notification |
| Subscription | Notification | Queried for target `DeviceSubscription`s (conformist to Session) |
| Platform Admin | Restaurant/Tenant | Customer/Supplier — tenant lifecycle |

## Domain Model

The domain layer is plain Java with no framework annotations. Aggregates protect their
invariants; state changes record domain events that the application layer publishes after a
successful transaction.

### Shared Kernel

```java
public record TenantId(UUID value) {
    public TenantId {
        Objects.requireNonNull(value, "tenantId must not be null");
    }
}

public interface DomainEvent {
    UUID eventId();
    TenantId tenantId();
    Instant occurredAt();
}
```

### Session Context — `CustomerSession` (primary aggregate)

Represents one customer's interaction context, created by scanning a QR code. It is the
correlation point that ties a device to its orders and subscriptions.

```java
public class CustomerSession {
    private final CustomerSessionId id;
    private final TenantId tenantId;
    private SessionStatus status;      // ACTIVE, ENDED
    private final Instant startedAt;
    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    // Factory: only way to create a session (no public constructor)
    public static CustomerSession start(TenantId tenantId, Clock clock) {
        var session = new CustomerSession(CustomerSessionId.newId(), tenantId,
                                          SessionStatus.ACTIVE, clock.instant());
        session.record(new CustomerSessionStarted(session.id, tenantId, clock.instant()));
        return session;
    }
    public boolean isActive() { return status == SessionStatus.ACTIVE; }
}
```

**Invariants**

- A `CustomerSession` always has a non-null `TenantId` assigned at creation (Req 1.6, 10.1).
- A session is created only for an existing, active Restaurant (Req 1.4, 1.7, 9.6).
- Creation requires no customer credentials (Req 1.5).
- Each scan produces a distinct session identity (Req 1.1).

### Ordering Context — `Order` aggregate + `OrderStatus` state machine

```java
public enum OrderStatus { CREATED, IN_PROGRESS, READY, COMPLETED, CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
        CREATED,     EnumSet.of(IN_PROGRESS, CANCELLED),
        IN_PROGRESS, EnumSet.of(READY, CANCELLED),
        READY,       EnumSet.of(COMPLETED, CANCELLED),
        COMPLETED,   EnumSet.noneOf(OrderStatus.class),
        CANCELLED,   EnumSet.noneOf(OrderStatus.class));

    public boolean canTransitionTo(OrderStatus target) {
        return ALLOWED.get(this).contains(target);
    }
    public boolean isTerminal() { return this == COMPLETED || this == CANCELLED; }
}

public class Order {
    private final OrderId id;
    private final TenantId tenantId;
    private final CustomerSessionId sessionId;
    private OrderStatus status;
    private long version;                 // optimistic lock guard (Req 5.8)
    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    public static Order create(TenantId tenantId, CustomerSessionId sessionId, Clock clock) {
        var order = new Order(OrderId.newId(), tenantId, sessionId, OrderStatus.CREATED);
        order.record(new OrderCreated(order.id, tenantId, sessionId, clock.instant()));
        return order;
    }

    public void transitionTo(OrderStatus target, Clock clock) {
        if (status == target)          throw new InvalidTransitionException(status, target); // Req 5.4
        if (status.isTerminal())       throw new TerminalStateException(status);             // Req 5.7
        if (!status.canTransitionTo(target)) throw new InvalidTransitionException(status, target); // Req 5.3
        var from = status;
        status = target;
        record(new OrderStatusUpdated(id, tenantId, sessionId, from, target, clock.instant())); // Req 5.5
        if (target == OrderStatus.READY) {
            record(new OrderReady(id, tenantId, sessionId, clock.instant()));                // Req 5.6
        }
    }
}
```

**Invariants**

- `Order` is created in `CREATED` (Req 4.1) with the creating staff's `TenantId` (Req 4.2)
  and an associated `CustomerSessionId` (Req 4.4).
- Transitions follow only `CREATED→IN_PROGRESS→READY→COMPLETED`, with `CANCELLED` reachable
  from `CREATED`/`IN_PROGRESS`/`READY` (Req 5.1, 5.2).
- No self-transition (Req 5.4); no transition out of a terminal state (Req 5.7).
- Concurrency guarded by `version` (optimistic locking) so at most one of concurrent
  transitions succeeds (Req 5.8).
- Reaching `READY` emits both `OrderStatusUpdated` and `OrderReady` (Req 5.5, 5.6).

### Subscription Context — `DeviceSubscription` aggregate

```java
public class DeviceSubscription {
    private final DeviceSubscriptionId id;
    private final TenantId tenantId;
    private final CustomerSessionId sessionId;
    private PushEndpoint endpoint;   // value object: URL + p256dh key + auth key
    // register / updateKeys / equalsEndpoint(...)
}

public record PushEndpoint(String endpointUrl, String p256dhKey, String authKey) {
    public PushEndpoint {
        require(endpointUrl); require(p256dhKey); require(authKey); // Req 3.3
    }
}
```

**Invariants**

- A subscription requires endpoint URL + both keys; partial data is rejected (Req 3.2, 3.3).
- A subscription belongs to exactly one `CustomerSession` and carries its `TenantId` (Req 3.1).
- The `(sessionId, endpointUrl)` pair is unique: re-registering the same endpoint updates
  rather than duplicates (Req 3.4).

### Restaurant/Tenant Context — `Restaurant` + `StaffAccount`

```java
public class Restaurant {
    private final TenantId tenantId;    // the unique tenant identifier (Req 9.2)
    private String identifier;          // unique business identifier (Req 9.5)
    private RestaurantStatus status;    // ACTIVE, DEACTIVATED (Req 9.3, 9.6)
    private RestaurantConfig config;
}

public class StaffAccount {
    private final StaffAccountId id;
    private final TenantId tenantId;
    private String username;            // unique within tenant (Req 8.4)
    private Role role;                  // STAFF, RESTAURANT_ADMIN
    private AccountStatus status;       // ACTIVE, DEACTIVATED (Req 8.5)
}
```

**Invariants**

- `Restaurant` gets a unique `TenantId` at creation (Req 9.2); business identifier is unique
  (Req 9.5).
- A deactivated Restaurant rejects new session starts (Req 9.6).
- `StaffAccount.username` is unique within a `TenantId` (Req 8.4); deactivation ends sessions
  and denies access (Req 8.5).

### Notification Context — `Notification`

```java
public class Notification {
    private final NotificationId id;
    private final TenantId tenantId;
    private final DeviceSubscriptionId subscriptionId;
    private NotificationStatus status;  // PENDING, SENT, DELIVERED, FAILED
    private final String payload;
}
```

## Domain Events and Event Flow

All events implement `DomainEvent` and are published through the `DomainEventPublisher` port
(Req 11). The domain records events on the aggregate; the application service publishes them
after the transaction commits.

| Event | Emitted by | Consumed by |
| --- | --- | --- |
| `CustomerSessionStarted` | Session (Req 1.2) | (audit / future analytics) |
| `OrderCreated` | Ordering (Req 4.3) | PWA/panel projections |
| `OrderStatusUpdated` | Ordering (Req 5.5) | PWA/panel projections |
| `OrderReady` | Ordering (Req 5.6) | **Notification** context |
| `NotificationSent` | Notification (Req 6.4) | (audit) |
| `NotificationDelivered` | Notification (Req 6.5) | (audit) |

### Order-Ready → Notification Flow

```
Ordering: order.transitionTo(READY)
   → records OrderStatusUpdated + OrderReady
   → application commits tx, then DomainEventPublisher.publish(events)
        │
        ▼ (in-process, via Spring ApplicationEventPublisher-backed adapter)
Notification: OrderReadyHandler.on(OrderReady)
   1. query DeviceSubscriptions for event.sessionId WHERE tenantId == event.tenantId  (Req 6.1)
   2. for each subscription: compose Notification, call NotificationSender.send(...)    (Req 6.2)
   3. on accept  → publish NotificationSent      (Req 6.4)
      on confirm → publish NotificationDelivered  (Req 6.5)
      on reject/timeout → record failure, continue with next subscription (Req 6.7, 6.8)
      on expired/invalid endpoint → delete subscription (Req 3.6)
   4. if no subscriptions → record "none delivered", no error (Req 6.6)
```

## Components and Interfaces

### Ports and Adapters

#### Inbound Ports (driven by adapters — REST controllers call these application services)

| Port (application service) | Use cases |
| --- | --- |
| `StartSessionUseCase` | Start a customer session from a QR payload |
| `ManageOrderUseCase` | Create order, transition order status |
| `ManageSubscriptionUseCase` | Register / update / delete device subscription |
| `RestaurantAdminUseCase` | Manage staff accounts + restaurant config |
| `PlatformAdminUseCase` | Create / update / deactivate restaurants |

#### Outbound Ports (defined in domain/application, implemented by adapters)

| Port | Purpose | Production adapter | Test adapter |
| --- | --- | --- | --- |
| `CustomerSessionRepository` | Persist/load sessions | JPA (tenant-filtered) | in-memory |
| `OrderRepository` | Persist/load orders | JPA (tenant-filtered) | in-memory |
| `DeviceSubscriptionRepository` | Persist/query subscriptions | JPA (tenant-filtered) | in-memory |
| `RestaurantRepository` | Persist/load restaurants | JPA | in-memory |
| `NotificationRepository` | Persist notifications | JPA (tenant-filtered) | in-memory |
| `DomainEventPublisher` | Publish domain events (Req 11) | Spring `ApplicationEventPublisher` | recording fake |
| `NotificationSender` | Deliver Web Push (Req 6.2) | VAPID Web Push | fake sender |
| `TenantContext` | Resolve current request `TenantId` | Redis/request-scoped | fixed value |

```java
// Broker-swappable event port — domain references ONLY this interface (Req 11.1, 11.2)
public interface DomainEventPublisher {
    void publish(DomainEvent event);        // throws EventPublicationException (Req 11.4)
    void publishAll(List<DomainEvent> events);
}

// Web Push port — VAPID details live in the adapter, not the domain (Req 6.2)
public interface NotificationSender {
    SendResult send(PushEndpoint endpoint, NotificationPayload payload);
    // SendResult: ACCEPTED | REJECTED | ENDPOINT_EXPIRED  (drives Req 3.6, 6.7, 6.8)
}
```

The default `DomainEventPublisher` adapter wraps Spring's `ApplicationEventPublisher`
(in-process). Replacing it with a Kafka/RabbitMQ adapter satisfies Req 11.5 with **no**
change to the domain source.

## Multi-Tenancy Strategy

Shared schema, `tenant_id` discriminator column on every tenant-scoped table, enforced at the
repository/query layer by the **Tenant_Context_Filter** (Req 10).

1. **Tenant resolution** — an inbound filter resolves the request's `TenantId` (from the
   authenticated staff/admin's tenant, or from the session's tenant for customer requests)
   and stores it in a request-scoped `TenantContext`. If none is resolvable for a
   tenant-scoped request, the request is rejected with a "tenant context missing" error and
   returns zero records (Req 10.5).
2. **Query enforcement** — a **Hibernate filter** (`@FilterDef`/`@Filter` on
   `tenant_id = :tenantId`) is enabled per session with the resolved `TenantId`, so every
   `SELECT` is automatically constrained (Req 10.2, 10.4). Cross-tenant records are excluded
   from reads and left unmodified on update/delete (Req 10.3).
3. **Write stamping** — a JPA entity listener (`@PrePersist`) stamps the current
   `TenantId` from `TenantContext` onto every tenant-scoped entity before commit (Req 10.1,
   10.6).

```java
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = UUID.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@MappedSuperclass
public abstract class TenantScopedEntity {
    @Column(name = "tenant_id", nullable = false, updatable = false)
    protected UUID tenantId;

    @PrePersist void stampTenant() {
        if (tenantId == null) tenantId = TenantContextHolder.require().value(); // Req 10.6
    }
}
```

## API-First Surface

Contracts are defined before implementation (OpenAPI). All endpoints are served over HTTPS
(Req 3.7). Customer endpoints are unauthenticated but session-scoped; panel/admin endpoints
require authentication and are tenant-scoped.

### Customer Session API (PWA, unauthenticated)

| Method | Path | Purpose | Requirements |
| --- | --- | --- | --- |
| `POST` | `/api/sessions` | Start session from QR payload `{qrToken}` | 1.1, 1.3–1.7, 9.6 |
| `GET` | `/api/sessions/{id}/orders` | List orders + states for session | 2.2, 2.5, 2.6 |

### Subscription API (PWA, session-scoped)

| Method | Path | Purpose | Requirements |
| --- | --- | --- | --- |
| `POST` | `/api/sessions/{id}/subscriptions` | Register/update push subscription | 3.1–3.4, 3.7 |
| `DELETE` | `/api/sessions/{id}/subscriptions/{subId}` | Revoke subscription | 3.5 |

### Order Management API (Restaurant Panel, staff auth, tenant-scoped)

| Method | Path | Purpose | Requirements |
| --- | --- | --- | --- |
| `POST` | `/api/orders` | Create order for a session | 4.1–4.6 |
| `GET` | `/api/orders` | List tenant's orders + states | 7.3, 7.7 |
| `PATCH` | `/api/orders/{id}/status` | Transition order status | 5.1–5.8, 6.x |

### Restaurant Admin API (admin auth, tenant-scoped)

| Method | Path | Purpose | Requirements |
| --- | --- | --- | --- |
| `POST/PUT/DELETE` | `/api/admin/staff` | Manage staff accounts | 8.3–8.5, 8.8 |
| `PUT` | `/api/admin/config` | Update restaurant config | 8.6, 8.7 |

### Platform Admin API (platform-admin auth, cross-tenant)

| Method | Path | Purpose | Requirements |
| --- | --- | --- | --- |
| `POST/PUT/DELETE` | `/api/platform/restaurants` | Manage tenants | 9.1–9.5 |

### Real-Time Updates to Clients

The PWA (Req 2.3, ≤5s) and Panel (Req 7.4, ≤10s) receive order-state changes via
Server-Sent Events (SSE) fed by the in-process `OrderStatusUpdated` handler, with polling
fallback. This is independent of Web Push (which targets background delivery).

## Data Models

PostgreSQL, shared schema. Every tenant-scoped table carries `tenant_id UUID NOT NULL`.

```
restaurant(tenant_id PK, identifier UNIQUE, status, config_json, created_at)
staff_account(id PK, tenant_id FK, username, role, status,
              UNIQUE(tenant_id, username))                         -- Req 8.4
customer_session(id PK, tenant_id, status, started_at)             -- Req 10.1
order(id PK, tenant_id, session_id FK, status, version, created_at,
      updated_at)                                                  -- version → Req 5.8
device_subscription(id PK, tenant_id, session_id FK, endpoint_url,
                    p256dh_key, auth_key,
                    UNIQUE(tenant_id, session_id, endpoint_url))    -- Req 3.4
notification(id PK, tenant_id, subscription_id FK, status, payload,
             created_at)
```

- Indexes on `(tenant_id)` and `(tenant_id, session_id)` support tenant-scoped queries.
- `order.version` column drives JPA `@Version` optimistic locking (Req 5.8).
- Subscription key columns are stored using encryption-at-rest (Req 3.8); TLS in transit
  (Req 3.7).

## Non-Functional Design

### Notification Latency < 2s (Req 6.3)

- `OrderReady` is handled **in-process** (no broker hop), so end-to-end latency is dominated
  by a single indexed subscription query + the `NotificationSender.send` accept.
- The handler measures elapsed time from event publication to sender accept; exceeding 2s is
  recorded as a failure for that subscription and processing continues (Req 6.7).
- Subscription lookup is a single indexed query on `(tenant_id, session_id)`.
- Sender calls per subscription run concurrently (bounded executor) so total latency does not
  grow linearly with subscription count.

### Horizontal Scalability & Availability (Req 12)

- **Stateless replicas** — no in-memory session/order state; ≥2 replicas behind a load
  balancer (Req 12.1, 12.2).
- **Externalized state** — session/cache state in Redis so any replica serves any request
  (Req 12.5).
- **Health checks** — Spring Boot Actuator `liveness`/`readiness` probes; Kubernetes stops
  routing to a failed replica within its probe window (Req 12.4) and continues via remaining
  replicas (Req 12.3).
- In-process events are per-replica; the `OrderReady` handler runs on the replica that
  processed the transition. When a broker is introduced (Req 11.5), handling becomes
  cross-replica without domain changes.

## Error Handling

| Condition | Handling | Requirements |
| --- | --- | --- |
| Malformed / unresolved QR | Reject, no session, "invalid QR" error | 1.4, 1.7 |
| Session for deactivated restaurant | Reject start with error | 9.6 |
| Missing subscription endpoint/keys | Reject, store nothing, identify missing data | 3.3 |
| Invalid order transition / self / terminal | Reject, keep state, "invalid transition" | 5.3, 5.4, 5.7 |
| Concurrent transitions | Optimistic lock → one wins, other retried/rejected | 5.8 |
| Cross-tenant access | Excluded from results, unmodified, error | 10.3, 7.7, 8.8 |
| Missing tenant context | Reject, zero records, "tenant context missing" | 10.5 |
| Event publication failure | Record, return error naming event, retain event | 11.4 |
| Sender reject/timeout | Record failure for that sub, continue | 6.7, 6.8 |
| Expired/invalid endpoint | Delete subscription, stop sending | 3.6 |
| Auth failure (panel/admin) | Deny, no session, "auth failed" | 7.2, 8.2 |

Errors surface as RFC 7807 `application/problem+json` responses with stable error codes.

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions
of a system — essentially, a formal statement about what the system should do. Properties
serve as the bridge between human-readable specifications and machine-verifiable correctness
guarantees.*

Each property-based test must run a minimum of 100 iterations and must be tagged with the
feature name (restaurant-notification-platform), the property number, and the property text.

### Property 1: Session start assigns tenant and distinct identity

*For any* well-formed QR payload that resolves to an existing, active Restaurant, starting a
session creates an ACTIVE `CustomerSession` whose `TenantId` equals that Restaurant's
`TenantId`, and any two session starts produce distinct `CustomerSessionId`s.

**Validates: Requirements 1.1, 1.6**

### Property 2: Session start publishes CustomerSessionStarted

*For any* successfully started `CustomerSession`, exactly one `CustomerSessionStarted` domain
event is published through the port, carrying the session's id and `TenantId`.

**Validates: Requirements 1.2**

### Property 3: Unresolvable or malformed QR is rejected with no session

*For any* QR payload that is malformed or that references a non-existent Restaurant, session
start is rejected, no `CustomerSession` is persisted, and an invalid-QR error is returned.

**Validates: Requirements 1.4, 1.7**

### Property 4: Deactivated restaurant rejects session start

*For any* Restaurant in the DEACTIVATED state, a session-start request for that Restaurant is
rejected with an error and no `CustomerSession` is created.

**Validates: Requirements 9.6**

### Property 5: Order projection exposes only valid lifecycle states

*For any* `CustomerSession` and its associated orders, the order projection returned to the
client lists each order with a state drawn from {CREATED, IN_PROGRESS, READY, COMPLETED,
CANCELLED} equal to the order's stored state.

**Validates: Requirements 2.2**

### Property 6: Valid subscription registration round-trips and is bound to session and tenant

*For any* active `CustomerSession` and any registration containing an endpoint URL and both
required keys, the registered `DeviceSubscription` is stored bound to that session's id and
`TenantId`, and loading it returns the same endpoint and keys.

**Validates: Requirements 3.1, 3.2**

### Property 7: Incomplete subscription registration is rejected with no partial store

*For any* subscription registration missing the endpoint URL or either required key, the
request is rejected, no partial `DeviceSubscription` is stored, and an error identifying the
missing data is returned.

**Validates: Requirements 3.3**

### Property 8: Re-registering an endpoint is idempotent per session

*For any* `CustomerSession` and endpoint, registering that same endpoint one or more times
results in exactly one `DeviceSubscription` for that `(session, endpoint)` pair, retaining
the most recently supplied keys.

**Validates: Requirements 3.4**

### Property 9: Revoked or invalid subscriptions receive no further messages

*For any* `DeviceSubscription`, after it is revoked by the customer or after the sender
reports its endpoint expired/invalid, the subscription is deleted and is absent from the set
of send targets for any subsequent notification.

**Validates: Requirements 3.5, 3.6**

### Property 10: A newly created order is well-formed

*For any* active `CustomerSession` in the creating staff member's tenant, the created `Order`
has status CREATED, carries the staff member's `TenantId`, and is associated with that
`CustomerSession`.

**Validates: Requirements 4.1, 4.2, 4.4**

### Property 11: OrderCreated is published on creation

*For any* successfully created `Order`, exactly one `OrderCreated` domain event is published
through the port.

**Validates: Requirements 4.3**

### Property 12: Order creation for an invalid or cross-tenant session is rejected

*For any* order-creation request whose target `CustomerSession` does not exist, is not
active, or has a `TenantId` differing from the staff member's tenant, the request is rejected
with an error and no `Order` is created.

**Validates: Requirements 4.5, 4.6**

### Property 13: Allowed lifecycle transitions succeed and land in the target state

*For any* `Order` and any source-to-target pair permitted by the Order_Lifecycle
(CREATED→IN_PROGRESS, IN_PROGRESS→READY, READY→COMPLETED, and CANCELLED from CREATED/
IN_PROGRESS/READY), applying the transition succeeds and leaves the order in the target state.

**Validates: Requirements 5.1, 5.2**

### Property 14: Disallowed, self, and post-terminal transitions are rejected without state change

*For any* `Order` and any transition that is not permitted by the Order_Lifecycle — including
a transition to the order's current state and any transition from COMPLETED or CANCELLED —
the transition is rejected, the order retains its current state unmodified, and an
invalid-transition error is returned.

**Validates: Requirements 5.3, 5.4, 5.7**

### Property 15: State changes and READY publish the correct events

*For any* successful order state transition, an `OrderStatusUpdated` event is published; and
whenever the target state is READY, an `OrderReady` event is also published.

**Validates: Requirements 5.5, 5.6**

### Property 16: Concurrent transitions apply at most one

*For any* two transitions requested concurrently on the same `Order`, at most one is applied
and the order is left in a single well-defined Order_Lifecycle state.

**Validates: Requirements 5.8**

### Property 17: OrderReady notifies exactly the matching subscriptions

*For any* `OrderReady` event and any set of stored `DeviceSubscription`s, a `Notification` is
composed and sent through the `NotificationSender` port for exactly those subscriptions whose
session equals the order's session and whose `TenantId` equals the order's `TenantId`, and
for no others.

**Validates: Requirements 6.1, 6.2**

### Property 18: Accept and delivery outcomes publish the corresponding events

*For any* notification whose send is accepted by the port, a `NotificationSent` event is
published; and for any notification whose delivery the port confirms, a
`NotificationDelivered` event is published.

**Validates: Requirements 6.4, 6.5**

### Property 19: Send and delivery failures are isolated

*For any* set of target subscriptions in which an arbitrary subset has its send rejected,
timed out, or its delivery reported failed, the failure is recorded for each affected
subscription and every remaining subscription is still processed without aborting the batch.

**Validates: Requirements 6.7, 6.8**

### Property 20: Staff account mutations are tenant-scoped

*For any* create/update/deactivate operation a Restaurant_Admin performs on a staff account,
the resulting account is associated with the admin's `TenantId`; and after deactivation, that
account's subsequent access is denied.

**Validates: Requirements 8.3, 8.5**

### Property 21: Invalid staff account and configuration changes are rejected without side effects

*For any* staff-account request with a missing required field or a duplicate username within
the same tenant, and *for any* configuration change that fails validation, the request is
rejected, existing data/configuration is left unchanged, and an error identifying the invalid
field/value is returned.

**Validates: Requirements 8.4, 8.7**

### Property 22: Restaurant creation assigns unique tenant identifiers and rejects invalid input

*For any* set of Restaurants created with valid data, each is assigned a `TenantId` distinct
from every other Restaurant's; and *for any* creation request with a missing required field
or a duplicate Restaurant identifier, the request is rejected and no Restaurant is created.

**Validates: Requirements 9.2, 9.5**

### Property 23: Tenant isolation across all tenant-scoped access

*For any* dataset containing records for multiple tenants and *for any* request bound to a
`TenantId` T, every record returned by a query has `TenantId` equal to T, and any read,
update, or delete targeting a record whose `TenantId` differs from T excludes that record
from results and leaves it unmodified.

**Validates: Requirements 10.2, 10.3, 10.4, 7.3, 7.7, 8.8**

### Property 24: Persisted tenant-scoped records are stamped with the request tenant

*For any* `CustomerSession`, `Order`, `Notification`, or `DeviceSubscription` persisted under
a resolved tenant context, the committed record's `TenantId` equals the request's `TenantId`.

**Validates: Requirements 10.1, 10.6**

### Property 25: All domain events flow through the swappable port

*For any* occurrence of a `CustomerSessionStarted`, `OrderCreated`, `OrderStatusUpdated`,
`OrderReady`, `NotificationSent`, or `NotificationDelivered` event, the event is published
through the `DomainEventPublisher` port; and *for any* alternative port implementation
substituted for the default, the same events are still published with no change to the domain
model source.

**Validates: Requirements 11.1, 11.3, 11.5**

### Property 26: Event publication failure is recorded and the event is retained

*For any* domain event whose publication the port rejects, the publication failure is
recorded, an error identifying the affected event is returned, and the event is retained
rather than discarded.

**Validates: Requirements 11.4**

## Testing Strategy

Complementary dual approach:

- **Property-based tests** (jqwik, ≥100 iterations) cover the universal properties above.
  Generators produce random tenants, sessions, orders, transition pairs, subscription sets,
  and mixed-tenant datasets. Outbound ports use fakes (recording `DomainEventPublisher`, fake
  `NotificationSender`, in-memory repositories) so domain/application logic is tested without
  I/O.
- **Example-based unit tests** cover discrete behaviors: no-auth session start (1.5), empty
  order list indication (2.5), stale-state client handling (2.6), auth success/failure
  scoping (7.1, 7.2, 8.1, 8.2, 9.1), capability wiring (7.5, 7.6, 9.3), non-admin denial
  (9.4), and config persistence round-trip (8.6).
- **Edge-case tests** cover missing tenant context (10.5) and empty-subscription OrderReady
  (6.6).
- **Structure/dependency tests** (ArchUnit-style) assert the domain layer imports no
  messaging or framework implementation (11.2).
- **Integration & smoke tests** cover timing/UI/infra NFRs not expressible as pure
  properties: PWA/panel delivery and latency (2.1, 2.3, 2.4, 6.3, 7.4), transport/at-rest
  encryption (3.7, 3.8), and replica/health-check/statelessness behavior (1.3, 12.1–12.5),
  including a two-instance test proving any replica serves any session via Redis.

## Architecture Decision Records

### ADR-001: Modular Monolith over Microservices (initially)

**Decision.** Ship one deployable with strict internal module boundaries mirroring bounded
contexts. **Context.** MVP scope, small team, need for fast iteration; contexts communicate
only via domain events and published application interfaces. **Consequences.** Lower
operational overhead now; extraction to microservices later is a refactor because boundaries
and the event port are already in place.

### ADR-002: Hexagonal (Ports & Adapters) with strict dependency rule

**Decision.** Domain defines ports; adapters implement them; dependencies point inward.
**Context.** Requirements demand a broker-swappable event mechanism (Req 11) and a
substitutable Web Push sender (Req 6.2). **Consequences.** Domain stays framework-free and
unit-testable with fakes; adapters (JPA, Redis, Web Push, Spring events) are swappable.

### ADR-003: In-process domain events behind DomainEventPublisher port

**Decision.** Publish events in-process via a Spring `ApplicationEventPublisher`-backed
adapter behind the `DomainEventPublisher` port; defer any external broker. **Context.** Req
11.1/11.2/11.5 require port-based publication and future broker swap without domain changes;
Req 6.3 requires <2s latency (no broker hop helps). **Consequences.** Simple, fast, single
-process ordering; cross-replica event fan-out is not available until a broker adapter is
added — an intentional, isolated future change.

### ADR-004: Shared-schema multi-tenancy with tenant_id discriminator + query-layer filter

**Decision.** Single PostgreSQL schema; `tenant_id` on every tenant-scoped table; a Hibernate
filter enforces `tenant_id = :tenantId` at the query layer and an entity listener stamps
tenant on write. **Context.** Req 10 requires isolation enforced at the repository layer;
shared schema is simplest to operate for MVP. **Alternatives.** Schema-per-tenant or
database-per-tenant (stronger isolation, higher operational cost) — deferred. **Consequences.**
Isolation is structural, not by convention; forgetting the filter is prevented by applying it
on a shared mapped superclass. Risk: a missing filter would leak data, mitigated by
Property 23 tests.

### ADR-005: Web Push via VAPID behind NotificationSender port

**Decision.** Define a `NotificationSender` port; production adapter sends VAPID-authenticated
Web Push; a fake adapter is used in tests. **Context.** Req 3, 6 require Web Push delivery and
handling of expired endpoints; tests must not perform real pushes. **Consequences.** Delivery
logic (Req 6.4–6.8, 3.6) is testable via the fake; VAPID keys/config stay in the adapter.

### ADR-006: Redis for externalized session/cache state

**Decision.** Store session/cache state in Redis; keep application instances stateless.
**Context.** Req 12.1/12.5 require stateless replicas so any replica serves any request.
**Consequences.** Horizontal scaling and rolling restarts are safe; adds Redis as an
operational dependency.

### ADR-007: Optimistic locking for order transitions

**Decision.** Guard `Order` with a JPA `@Version` column. **Context.** Req 5.8 requires at
most one of concurrent transitions to apply. **Alternatives.** Pessimistic DB locks (higher
contention cost). **Consequences.** Concurrent transitions cause one to fail fast and retry/
reject; verified by Property 16.

### ADR-008: Introduce Spring Boot + Java 21 into a bare Gradle project

**Decision.** Add Spring Boot 3.x, Spring Data JPA, Spring Data Redis, and jqwik to the
existing Gradle Kotlin DSL build (currently only the `java` plugin + JUnit). **Context.** The
requirements mandate this stack; the project is greenfield. **Consequences.** Build gains the
Spring dependency management plugin and a bootable application; the placeholder
`org.example.Main` is replaced by an `org.readybell` Spring application entry point.

### ADR-009: API-first with SSE for live updates, Web Push for background

**Decision.** Define OpenAPI contracts before implementation; push live in-app order-state
updates over Server-Sent Events (PWA ≤5s, Panel ≤10s) and use Web Push only for background
order-ready alerts. **Context.** Req 2.3/7.4 need near-real-time in-app updates distinct from
background notifications (Req 6). **Consequences.** Clear separation of live UI updates from
background delivery; SSE is simple and load-balancer friendly with a polling fallback.
