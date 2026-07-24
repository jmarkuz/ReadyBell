# Implementation Plan: ReadyBell — Restaurant Customer Notification Platform

## Overview

This plan implements ReadyBell domain-first, following the Clean/Hexagonal dependency rule
(domain → application → adapters) inside an event-driven modular monolith. Work proceeds in
the order dictated by the development strategy: (1) bootstrap the stack and shared kernel,
(2) build the domain aggregates and their invariants with property-based tests, (3) define
outbound ports and fakes, (4) implement application services / use cases (QR → session →
order → notification), (5) add production adapters (tenant-filtered JPA, Spring event
publisher, VAPID sender) and multi-tenancy enforcement, (6) wire inbound REST + SSE and the
composition root, then verify the non-functional requirements.

The domain layer stays framework-free; every aggregate is built and tested against fakes
before any Spring/JPA adapter exists. All 26 correctness properties from the design are
covered by jqwik property-based tests (≥100 iterations, tagged with the feature name
`restaurant-notification-platform`, the property number, and the property text). Property and
unit test sub-tasks are marked optional with `*`.

Implementation language: **Java 21** (per the design and ADR-008). All code lives under the
`org.readybell` package following the modular-monolith package layout in the design.

## Tasks

- [x] 1. Bootstrap the Spring Boot + Java 21 stack into the bare Gradle project
  - [x] 1.1 Convert the Gradle Kotlin DSL build to a Spring Boot application build
    - Edit `build.gradle.kts`: apply the Spring Boot 3.x plugin and the Spring dependency-management plugin, set Java toolchain to 21, change `group` to `org.readybell`
    - Add dependencies: `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `spring-boot-starter-data-redis`, `spring-boot-starter-actuator`, PostgreSQL driver (runtime), a Web Push/VAPID library
    - Add test dependencies: `spring-boot-starter-test`, JUnit 5 (BOM already present), and `net.jqwik:jqwik` with the jqwik JUnit 5 engine; keep `useJUnitPlatform()`
    - _Requirements: 11 (broker-swappable eventing stack), 12.1 (bootable stateless app); ADR-008_

  - [x] 1.2 Replace the placeholder entry point and create the modular-monolith package skeleton
    - Delete `org.example.Main`; create `org.readybell.ReadyBellApplication` with `@SpringBootApplication`
    - Create the top-level context packages (`shared`, `session`, `ordering`, `notification`, `subscription`, `restaurant`, `platformadmin`, `platform`) each with the hexagonal sub-package shape (`domain`, `application`, `adapter.in.web`, `adapter.out.persistence`) as described in the design
    - Add a base `application.yml` for datasource, Redis, and Actuator liveness/readiness probes
    - _Requirements: 12.1, 12.4; ADR-001, ADR-008_

  - [ ]* 1.3 Write a context-load smoke test
    - Verify the Spring `ApplicationContext` boots (with test profile) so later wiring has a baseline
    - _Requirements: 12.1_

- [x] 2. Implement the shared kernel
  - [x] 2.1 Create core shared value objects and the domain event contract
    - Implement `TenantId` (record wrapping non-null `UUID`) and the `DomainEvent` interface (`eventId()`, `tenantId()`, `occurredAt()`) as plain Java with no framework imports
    - _Requirements: 10.1, 11.1, 11.2_

  - [x] 2.2 Create the tenant context holder
    - Implement `TenantContext` and `TenantContextHolder` (request-scoped resolution + `require()` that fails with a "tenant context missing" signal when unresolved)
    - _Requirements: 10.4, 10.5_

  - [ ]* 2.3 Write unit tests for shared kernel primitives
    - Test `TenantId` null rejection and `TenantContextHolder.require()` behavior when empty
    - _Requirements: 10.5_

- [x] 3. Implement the Session domain aggregate
  - [x] 3.1 Implement `CustomerSession` aggregate
    - Create `CustomerSessionId`, `SessionStatus` (ACTIVE, ENDED), the `CustomerSession` aggregate with the `start(TenantId, Clock)` factory (no public constructor, no credentials), pending-event recording, and the `CustomerSessionStarted` domain event
    - _Requirements: 1.1, 1.5, 1.6, 10.1_

  - [ ]* 3.2 Write property test for session start identity and tenant assignment
    - **Property 1: Session start assigns tenant and distinct identity**
    - **Validates: Requirements 1.1, 1.6**

  - [ ]* 3.3 Write property test for session-started event emission
    - **Property 2: Session start publishes CustomerSessionStarted**
    - **Validates: Requirements 1.2**

  - [ ]* 3.4 Write unit test for credential-free creation
    - Assert a session is created without any authentication input (Req 1.5)
    - _Requirements: 1.5_

- [x] 4. Implement the Ordering domain aggregate and state machine
  - [x] 4.1 Implement the `OrderStatus` state machine
    - Create the `OrderStatus` enum with the allowed-transition map, `canTransitionTo`, and `isTerminal`
    - _Requirements: 5.1, 5.2, 5.7_

  - [ ]* 4.2 Write property test for allowed transitions
    - **Property 13: Allowed lifecycle transitions succeed and land in the target state**
    - **Validates: Requirements 5.1, 5.2**

  - [ ]* 4.3 Write property test for disallowed/self/terminal transitions
    - **Property 14: Disallowed, self, and post-terminal transitions are rejected without state change**
    - **Validates: Requirements 5.3, 5.4, 5.7**

  - [x] 4.4 Implement the `Order` aggregate
    - Create `OrderId`, the `Order` aggregate with `create(...)` factory (starts in CREATED, carries `TenantId` + `CustomerSessionId`), `transitionTo(...)` enforcing the invariants, a `version` field for optimistic locking, and the `OrderCreated`, `OrderStatusUpdated`, `OrderReady` events plus `InvalidTransitionException` / `TerminalStateException`
    - _Requirements: 4.1, 4.2, 4.4, 5.3, 5.4, 5.5, 5.6, 5.7_

  - [ ]* 4.5 Write property test for well-formed order creation
    - **Property 10: A newly created order is well-formed**
    - **Validates: Requirements 4.1, 4.2, 4.4**

  - [ ]* 4.6 Write property test for OrderCreated emission
    - **Property 11: OrderCreated is published on creation**
    - **Validates: Requirements 4.3**

  - [ ]* 4.7 Write property test for status-change and READY event emission
    - **Property 15: State changes and READY publish the correct events**
    - **Validates: Requirements 5.5, 5.6**

- [x] 5. Implement the Subscription domain aggregate
  - [x] 5.1 Implement `DeviceSubscription` aggregate and `PushEndpoint` value object
    - Create `DeviceSubscriptionId`, the `PushEndpoint` record (validates endpoint URL + `p256dh` + `auth`, rejecting partial data), and the `DeviceSubscription` aggregate bound to one `CustomerSessionId` + `TenantId` with `register`/`updateKeys`/`equalsEndpoint`
    - _Requirements: 3.1, 3.2, 3.3_

  - [ ]* 5.2 Write property test for incomplete-registration rejection
    - **Property 7: Incomplete subscription registration is rejected with no partial store**
    - **Validates: Requirements 3.3**

- [x] 6. Implement the Restaurant/Tenant domain aggregates
  - [x] 6.1 Implement the `Restaurant` aggregate
    - Create `Restaurant` with `TenantId`, unique business `identifier`, `RestaurantStatus` (ACTIVE, DEACTIVATED), and `RestaurantConfig`; expose behavior to deactivate and to reject new sessions while deactivated
    - _Requirements: 9.2, 9.5, 9.6_

  - [x] 6.2 Implement the `StaffAccount` aggregate
    - Create `StaffAccountId`, `Role` (STAFF, RESTAURANT_ADMIN), `AccountStatus` (ACTIVE, DEACTIVATED), and the `StaffAccount` aggregate with username-unique-within-tenant intent and deactivation behavior
    - _Requirements: 8.3, 8.4, 8.5_

  - [ ]* 6.3 Write unit tests for restaurant and staff invariants
    - Test deactivated-restaurant session rejection and staff deactivation state changes
    - _Requirements: 9.6, 8.5_

- [x] 7. Implement the Notification domain aggregate
  - [x] 7.1 Implement the `Notification` aggregate
    - Create `NotificationId`, `NotificationStatus` (PENDING, SENT, DELIVERED, FAILED), the `Notification` aggregate, and the `NotificationSent` / `NotificationDelivered` domain events
    - _Requirements: 6.4, 6.5_

  - [ ]* 7.2 Write unit tests for notification status transitions
    - Test PENDING → SENT → DELIVERED and PENDING → FAILED transitions and event recording
    - _Requirements: 6.4, 6.5_

- [x] 8. Checkpoint - domain model complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Define outbound ports and their test fakes
  - [x] 9.1 Define the `DomainEventPublisher` port and its recording fake
    - Create the `DomainEventPublisher` interface (`publish`, `publishAll`, `EventPublicationException`) and a recording fake capturing published events; domain references only the interface
    - _Requirements: 11.1, 11.2, 11.3, 11.4_

  - [x] 9.2 Define the `NotificationSender` port and its fake
    - Create the `NotificationSender` interface with `SendResult` (ACCEPTED, REJECTED, ENDPOINT_EXPIRED) and a configurable fake sender driving accept/reject/expired outcomes
    - _Requirements: 6.2, 6.7, 6.8, 3.6_

  - [x] 9.3 Define the repository ports and in-memory fakes
    - Create `CustomerSessionRepository`, `OrderRepository`, `DeviceSubscriptionRepository`, `RestaurantRepository`, `NotificationRepository` interfaces and tenant-aware in-memory fakes for testing application logic without I/O
    - _Requirements: 10.2, 10.4_

  - [x] 9.4 Define the `TenantContext` resolution port and a fixed-value fake
    - Create the `TenantContext` port used by application services and a fixed-tenant fake for tests
    - _Requirements: 10.4, 10.5_

  - [ ]* 9.5 Write property test that all domain events flow through the swappable port
    - **Property 25: All domain events flow through the swappable port**
    - **Validates: Requirements 11.1, 11.3, 11.5**

  - [ ]* 9.6 Write property test for event-publication-failure handling
    - **Property 26: Event publication failure is recorded and the event is retained**
    - **Validates: Requirements 11.4**

- [x] 10. Implement the StartSession use case
  - [x] 10.1 Implement `StartSessionUseCase` and its service
    - Parse the QR payload, resolve the Restaurant via `RestaurantRepository`, reject malformed/unresolved/deactivated restaurants, create + persist the session, and publish recorded events through `DomainEventPublisher`
    - _Requirements: 1.1, 1.4, 1.6, 1.7, 9.6_

  - [ ]* 10.2 Write property test for malformed/unresolvable QR rejection
    - **Property 3: Unresolvable or malformed QR is rejected with no session**
    - **Validates: Requirements 1.4, 1.7**

  - [ ]* 10.3 Write property test for deactivated-restaurant session rejection
    - **Property 4: Deactivated restaurant rejects session start**
    - **Validates: Requirements 9.6**

- [x] 11. Implement the ManageOrder use case
  - [x] 11.1 Implement order creation in `ManageOrderUseCase`
    - Validate the target session exists, is active, and shares the staff tenant; create + persist the order and publish `OrderCreated`
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6_

  - [ ]* 11.2 Write property test for invalid/cross-tenant order creation
    - **Property 12: Order creation for an invalid or cross-tenant session is rejected**
    - **Validates: Requirements 4.5, 4.6**

  - [x] 11.3 Implement order status transition in `ManageOrderUseCase`
    - Load the order under the tenant context, apply `transitionTo`, persist under optimistic locking so at most one concurrent transition wins, and publish `OrderStatusUpdated` (+ `OrderReady` on READY)
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8_

  - [ ]* 11.4 Write property test for concurrent transitions
    - **Property 16: Concurrent transitions apply at most one**
    - **Validates: Requirements 5.8**

- [x] 12. Implement the ManageSubscription use case
  - [x] 12.1 Implement register/update/delete in `ManageSubscriptionUseCase`
    - Register a subscription bound to session + tenant, update the existing subscription when the same endpoint re-registers, delete on customer revoke, and delete on sender-reported expired/invalid endpoint so no further messages target it
    - _Requirements: 3.1, 3.2, 3.4, 3.5, 3.6_

  - [ ]* 12.2 Write property test for valid registration round-trip
    - **Property 6: Valid subscription registration round-trips and is bound to session and tenant**
    - **Validates: Requirements 3.1, 3.2**

  - [ ]* 12.3 Write property test for idempotent endpoint re-registration
    - **Property 8: Re-registering an endpoint is idempotent per session**
    - **Validates: Requirements 3.4**

  - [ ]* 12.4 Write property test for revoked/invalid subscription exclusion
    - **Property 9: Revoked or invalid subscriptions receive no further messages**
    - **Validates: Requirements 3.5, 3.6**

- [x] 13. Implement the Notification OrderReady handler and order-ready flow
  - [x] 13.1 Implement `OrderReadyHandler` and the notification composition flow
    - On `OrderReady`, query `DeviceSubscription`s for the event's session where `TenantId` matches, compose a `Notification` per subscription, send via `NotificationSender` concurrently, publish `NotificationSent` on accept and `NotificationDelivered` on confirm, record failures/timeouts per subscription and continue, delete expired/invalid endpoints, and record "none delivered" when there are no subscriptions
    - _Requirements: 6.1, 6.2, 6.4, 6.5, 6.6, 6.7, 6.8, 3.6_

  - [ ]* 13.2 Write property test for matching-subscription targeting
    - **Property 17: OrderReady notifies exactly the matching subscriptions**
    - **Validates: Requirements 6.1, 6.2**

  - [ ]* 13.3 Write property test for accept/delivery event publication
    - **Property 18: Accept and delivery outcomes publish the corresponding events**
    - **Validates: Requirements 6.4, 6.5**

  - [ ]* 13.4 Write property test for failure isolation across a batch
    - **Property 19: Send and delivery failures are isolated**
    - **Validates: Requirements 6.7, 6.8**

  - [ ]* 13.5 Write edge-case unit test for empty-subscription OrderReady
    - Assert no notification is delivered and no error is raised when the session has no subscriptions
    - _Requirements: 6.6_

- [x] 14. Implement the RestaurantAdmin use case
  - [x] 14.1 Implement `RestaurantAdminUseCase`
    - Create/update/deactivate staff accounts scoped to the admin's tenant (deactivation denies subsequent access), persist tenant-scoped configuration changes, and reject invalid/duplicate/cross-tenant operations leaving existing data unchanged
    - _Requirements: 8.3, 8.4, 8.5, 8.6, 8.7, 8.8_

  - [ ]* 14.2 Write property test for tenant-scoped staff mutations
    - **Property 20: Staff account mutations are tenant-scoped**
    - **Validates: Requirements 8.3, 8.5**

  - [ ]* 14.3 Write property test for invalid staff/config rejection
    - **Property 21: Invalid staff account and configuration changes are rejected without side effects**
    - **Validates: Requirements 8.4, 8.7**

- [x] 15. Implement the PlatformAdmin use case
  - [x] 15.1 Implement `PlatformAdminUseCase`
    - Create restaurants with a unique `TenantId`, update and deactivate restaurants, reject creation with missing fields or duplicate identifiers (creating nothing), and deny tenant-management to non-platform-admins
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5_

  - [ ]* 15.2 Write property test for unique tenant assignment and invalid-input rejection
    - **Property 22: Restaurant creation assigns unique tenant identifiers and rejects invalid input**
    - **Validates: Requirements 9.2, 9.5**

  - [ ]* 15.3 Write unit test for non-admin denial
    - Assert a non-platform-admin request to a tenant-management function is denied
    - _Requirements: 9.4_

- [x] 16. Checkpoint - core flow complete against fakes
  - Ensure all tests pass, ask the user if questions arise.

- [x] 17. Implement persistence adapters and multi-tenancy enforcement
  - [x] 17.1 Implement the tenant-scoped JPA base and enforcement mechanics
    - Create the `TenantScopedEntity` `@MappedSuperclass` with the Hibernate `@FilterDef`/`@Filter` (`tenant_id = :tenantId`) and the `@PrePersist` listener stamping the current tenant from `TenantContextHolder`
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.6; ADR-004_

  - [x] 17.2 Implement JPA entities and tenant-filtered repository adapters
    - Map `restaurant`, `staff_account`, `customer_session`, `order` (with `@Version`), `device_subscription` (unique `(tenant_id, session_id, endpoint_url)`), and `notification` tables; implement the repository ports as Spring Data JPA adapters that enable the tenant filter per session, with the required indexes
    - _Requirements: 3.4, 5.8, 8.4, 10.1, 10.2, 10.4, 10.6; ADR-007_

  - [x] 17.3 Implement tenant resolution filter and the Redis-backed TenantContext adapter
    - Add an inbound request filter that resolves the request `TenantId` (from authenticated staff/admin tenant or session tenant) into a request-scoped `TenantContext`, rejecting tenant-scoped requests with no resolvable tenant; externalize session/cache state to Redis
    - _Requirements: 10.4, 10.5, 12.5; ADR-006_

  - [ ]* 17.4 Write property test for tenant isolation across all tenant-scoped access
    - **Property 23: Tenant isolation across all tenant-scoped access**
    - **Validates: Requirements 10.2, 10.3, 10.4, 7.3, 7.7, 8.8**

  - [ ]* 17.5 Write property test for tenant stamping on persisted records
    - **Property 24: Persisted tenant-scoped records are stamped with the request tenant**
    - **Validates: Requirements 10.1, 10.6**

  - [ ]* 17.6 Write edge-case test for missing tenant context
    - Assert a tenant-scoped request with no resolvable tenant is rejected, returns zero records, and produces a "tenant context missing" error
    - _Requirements: 10.5_

- [x] 18. Implement the DomainEventPublisher production adapter
  - [x] 18.1 Implement the Spring `ApplicationEventPublisher`-backed adapter
    - Implement `DomainEventPublisher` over Spring's in-process event publisher and register the `OrderReadyHandler` as an in-process listener; on publication failure, record it, retain the event, and surface an error naming the event
    - _Requirements: 11.1, 11.3, 11.4, 11.5; ADR-003_

  - [ ]* 18.2 Write structure/dependency test for domain purity
    - ArchUnit-style test asserting the domain layer imports no messaging or framework implementation
    - _Requirements: 11.2_

- [x] 19. Implement the NotificationSender VAPID adapter
  - [x] 19.1 Implement the VAPID Web Push adapter
    - Implement `NotificationSender` using the Web Push/VAPID library, mapping HTTP outcomes to `ACCEPTED`/`REJECTED`/`ENDPOINT_EXPIRED`, keeping VAPID keys/config in the adapter only
    - _Requirements: 6.2, 6.7, 6.8, 3.6; ADR-005_

  - [ ]* 19.2 Write integration test for the sender adapter outcome mapping
    - Exercise the adapter against a stubbed push endpoint to verify accept/reject/expired mapping
    - _Requirements: 6.2, 3.6_

- [x] 20. Implement inbound REST controllers and SSE live updates
  - [x] 20.1 Implement the Customer Session API
    - `POST /api/sessions` (start from `{qrToken}`) and `GET /api/sessions/{id}/orders` (list orders + states, empty indication), served over HTTPS with RFC 7807 error responses
    - _Requirements: 1.1, 1.3, 1.4, 1.5, 1.6, 1.7, 2.2, 2.5, 9.6_

  - [x] 20.2 Implement the Subscription API
    - `POST /api/sessions/{id}/subscriptions` (register/update) and `DELETE /api/sessions/{id}/subscriptions/{subId}` (revoke) over encrypted channels
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.7_

  - [x] 20.3 Implement the Order Management API
    - `POST /api/orders` (create for a session), `GET /api/orders` (list tenant orders + states), `PATCH /api/orders/{id}/status` (transition), tenant-scoped with cross-tenant access denied
    - _Requirements: 4.1, 4.5, 4.6, 5.1, 5.3, 5.4, 5.7, 7.3, 7.5, 7.6, 7.7_

  - [x] 20.4 Implement the Restaurant Admin API and staff authentication scoping
    - `POST/PUT/DELETE /api/admin/staff` and `PUT /api/admin/config`, with authentication granting tenant-scoped access and denying cross-tenant operations
    - _Requirements: 7.1, 7.2, 8.1, 8.2, 8.3, 8.6, 8.8_

  - [x] 20.5 Implement the Platform Admin API
    - `POST/PUT/DELETE /api/platform/restaurants` with platform-admin authentication and non-admin denial
    - _Requirements: 9.1, 9.3, 9.4_

  - [x] 20.6 Implement SSE live order-state updates
    - Feed an SSE stream from the in-process `OrderStatusUpdated` handler for PWA (≤5s) and Panel (≤10s) consumers, with a polling fallback endpoint
    - _Requirements: 2.3, 7.4; ADR-009_

  - [ ]* 20.7 Write unit tests for authentication scoping and denial
    - Test staff/admin/platform auth success scoping and failure denial, and cross-tenant order access denial
    - _Requirements: 7.1, 7.2, 7.7, 8.1, 8.2, 9.1_

  - [ ]* 20.8 Write property test for order projection state values
    - **Property 5: Order projection exposes only valid lifecycle states**
    - **Validates: Requirements 2.2**

- [x] 21. Wire the composition root
  - [x] 21.1 Implement Spring configuration wiring ports to production adapters
    - Bind repositories, `DomainEventPublisher`, `NotificationSender`, and `TenantContext` to their production adapters; configure Spring Security for panel/admin/platform endpoints and register the tenant resolution filter, leaving no orphaned components
    - _Requirements: 7.1, 8.1, 9.1, 10.4, 11.1_

  - [ ]* 21.2 Write an end-to-end integration test of the QR → session → order → notification flow
    - Drive the wired application (fake sender) from session start through order READY and assert a notification is composed for the session's subscription
    - _Requirements: 1.1, 4.1, 5.6, 6.1_

- [x] 22. Checkpoint - full stack wired
  - Ensure all tests pass, ask the user if questions arise.

- [x] 23. Verify non-functional requirements
  - [ ]* 23.1 Write the notification-latency verification test
    - Automated test asserting the `NotificationSender` accepts the push within 2s of `OrderReady`, and that exceeding the window is recorded as a failure and processing continues
    - _Requirements: 6.3, 6.7_

  - [ ]* 23.2 Write the statelessness / multi-replica test
    - Automated test proving any replica serves any active session via Redis-externalized state (two-instance scenario), and that request routing continues when one instance is unavailable
    - _Requirements: 12.1, 12.2, 12.3, 12.5_

  - [ ]* 23.3 Write the health-check and encryption verification tests
    - Assert Actuator liveness/readiness probes drive replica removal within the probe window, and verify TLS-in-transit and encryption-at-rest configuration for subscription data
    - _Requirements: 12.4, 3.7, 3.8_

- [x] 24. Final checkpoint - ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional test tasks and can be skipped for a faster MVP, but the
  26 property-based tests are how the design's correctness properties are verified.
- Property-based tests use jqwik at ≥100 iterations and must be tagged with the feature name
  (`restaurant-notification-platform`), the property number, and the property text.
- The domain aggregates (tasks 3–7) are built and tested entirely against fakes (task 9)
  before any Spring/JPA adapter exists, preserving the Clean Architecture dependency rule.
- Every task references the specific requirement sub-clauses it implements for traceability.
- Checkpoints (tasks 8, 16, 22, 24) provide incremental validation at the end of each
  development-strategy phase.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "2.1", "2.2"] },
    { "id": 2, "tasks": ["1.3", "2.3", "3.1", "4.1", "5.1", "6.1", "7.1"] },
    { "id": 3, "tasks": ["3.2", "3.3", "3.4", "4.2", "4.3", "4.4", "5.2", "6.2", "7.2"] },
    { "id": 4, "tasks": ["4.5", "4.6", "4.7", "6.3", "9.1", "9.2", "9.3", "9.4"] },
    { "id": 5, "tasks": ["9.5", "9.6", "10.1", "11.1", "11.3", "12.1", "13.1", "14.1", "15.1"] },
    { "id": 6, "tasks": ["10.2", "10.3", "11.2", "11.4", "12.2", "12.3", "12.4", "13.2", "13.3", "13.4", "13.5", "14.2", "14.3", "15.2", "15.3", "17.1", "17.2", "17.3", "18.1", "19.1"] },
    { "id": 7, "tasks": ["17.4", "17.5", "17.6", "18.2", "19.2", "20.1", "20.2", "20.3", "20.4", "20.5", "20.6"] },
    { "id": 8, "tasks": ["20.7", "20.8", "21.1"] },
    { "id": 9, "tasks": ["21.2", "23.1", "23.2", "23.3"] }
  ]
}
```
