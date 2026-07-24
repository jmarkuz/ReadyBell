# Requirements Document

## Introduction

The Restaurant Customer Notification Platform (product name "ReadyBell") is a multi-tenant system that lets restaurant customers track the status of their orders and receive real-time notifications without installing a native app or creating an account. A customer scans a QR code to start a session, opens a Progressive Web Application (PWA), and opts in to Web Push notifications. Restaurant staff manage orders and their statuses through a web panel, and restaurant administrators manage staff and restaurant configuration. A platform administrator manages tenants across the whole system.

This document specifies the "Must Have" MVP scope: QR-based session start, PWA interface, Web Push notifications via VAPID, order status tracking, a restaurant web panel for staff and admins, and multi-tenant isolation. Prep-time estimates, an analytics dashboard, and loyalty/third-party integrations are explicitly out of scope for this MVP.

The system is designed domain-first following Domain-Driven Design and Clean/Hexagonal architecture, as an event-driven modular monolith. Domain events are published in-process behind hexagonal ports so the messaging mechanism can be swapped for an external broker later. Persistence uses a shared PostgreSQL schema with a tenant discriminator enforced at the query layer. Web Push delivery is performed through a NotificationSender port backed by a VAPID implementation.

## Glossary

- **ReadyBell_Platform**: The complete multi-tenant system, including all backend services and web/PWA frontends described in this document.
- **Session_Service**: The component responsible for creating and managing customer sessions.
- **Order_Service**: The component responsible for creating orders and managing order lifecycle transitions.
- **Notification_Service**: The component responsible for composing notifications and delivering them through the NotificationSender port.
- **Subscription_Service**: The component responsible for registering, storing, and removing customer device push subscriptions.
- **Tenant_Context_Filter**: The query-layer mechanism that constrains every data access to a single tenant using the tenant identifier.
- **Restaurant_Panel**: The web application used by Restaurant Staff and Restaurant Admins.
- **Customer**: A restaurant patron who uses the PWA to track an order. A Customer does not authenticate in the MVP.
- **Restaurant_Staff**: An authenticated user who manages orders and order statuses for one Restaurant.
- **Restaurant_Admin**: An authenticated user who manages Restaurant configuration and Restaurant Staff accounts for one Restaurant.
- **Platform_Admin**: An authenticated user who manages Restaurants (tenants) across the ReadyBell Platform.
- **Restaurant**: A tenant of the ReadyBell Platform. Each Restaurant has a unique tenant identifier.
- **Tenant_Id**: The unique identifier that associates every record and request with exactly one Restaurant.
- **Customer_Session**: The primary domain entity representing one Customer's interaction context, started by scanning a QR code and used to associate a device with orders and notifications.
- **Order**: A domain entity representing a Customer order that progresses through the Order Lifecycle.
- **Order_Lifecycle**: The set of allowed Order states and transitions: CREATED, IN_PROGRESS, READY, COMPLETED, and CANCELLED.
- **Notification**: A domain entity representing a message intended for delivery to a Customer device.
- **Device_Subscription**: A domain entity representing a Web Push subscription for one Customer device, including endpoint and keys required to send push messages.
- **QR_Code**: A scannable code that encodes the information required to start a Customer_Session for a specific Restaurant.
- **PWA**: The Progressive Web Application that Customers use to view order status and manage push subscriptions.
- **Web_Push**: The browser push notification mechanism used to deliver Notifications.
- **VAPID**: The Voluntary Application Server Identification keys used to authenticate Web Push messages.
- **NotificationSender_Port**: The hexagonal outbound port through which the Notification_Service delivers Notifications.
- **Domain_Event**: An in-process event published behind a hexagonal port that represents a state change in the domain.
- **Notification_Latency**: The elapsed time between publication of the OrderReady Domain_Event and the NotificationSender_Port accepting the corresponding Web_Push message for delivery.

## Requirements

### Requirement 1: QR-Based Session Start

**User Story:** As a Customer, I want to start a session by scanning a QR code, so that I can track my order without installing an app or logging in.

#### Acceptance Criteria

1. WHEN a Customer opens the PWA using a QR_Code that is well-formed and resolves to an existing Restaurant, THE Session_Service SHALL create a new Customer_Session associated with that Restaurant, distinct from any Customer_Session created by a prior or concurrent scan.
2. WHEN the Session_Service creates a Customer_Session, THE Session_Service SHALL publish a CustomerSessionStarted Domain_Event.
3. WHEN a Customer opens the PWA using a QR_Code that is well-formed and resolves to an existing Restaurant, THE Session_Service SHALL make the Customer_Session available to the Customer within 10 seconds of the PWA being opened.
4. IF the QR_Code is well-formed but does not resolve to an existing Restaurant, THEN THE Session_Service SHALL reject the session start, SHALL NOT create a Customer_Session, and SHALL return an error response identifying the QR_Code as invalid.
5. THE Session_Service SHALL create a Customer_Session without requiring the Customer to provide authentication credentials.
6. WHEN the Session_Service creates a Customer_Session, THE Session_Service SHALL assign the Tenant_Id of the Restaurant encoded in the QR_Code to the Customer_Session.
7. IF the QR_Code is malformed or cannot be parsed to identify a Restaurant, THEN THE Session_Service SHALL reject the session start, SHALL NOT create a Customer_Session, and SHALL return an error response identifying the QR_Code as invalid.

### Requirement 2: PWA Customer Interface

**User Story:** As a Customer, I want a mobile-first web interface, so that I can view my order status on my phone with zero friction.

#### Acceptance Criteria

1. WHEN a Customer opens the PWA, THE ReadyBell_Platform SHALL serve an installable Progressive Web Application over HTTPS whose initial interactive view becomes available to the Customer within 5 seconds.
2. WHILE a Customer_Session is active, THE PWA SHALL display the current state of each Order associated with the Customer_Session using the Order_Lifecycle state values (CREATED, IN_PROGRESS, READY, COMPLETED, CANCELLED).
3. WHEN the state of an Order associated with an active Customer_Session changes, THE PWA SHALL update the displayed Order state to reflect the new state within 5 seconds of the change.
4. THE PWA SHALL render the customer interface without horizontal scrolling for viewport widths from 320 to 480 CSS pixels.
5. WHILE a Customer_Session is active and has no associated Order, THE PWA SHALL display an indication that no Orders exist for the Customer_Session.
6. IF the PWA cannot retrieve the current state of an Order for an active Customer_Session, THEN THE PWA SHALL display an error indication that the Order state is unavailable and retain the last successfully displayed Order state.

### Requirement 3: Web Push Subscription Management

**User Story:** As a Customer, I want to opt in to push notifications, so that I am alerted when my order is ready even if the PWA is not in the foreground.

#### Acceptance Criteria

1. WHEN a Customer grants Web_Push permission within an active Customer_Session, THE Subscription_Service SHALL register a Device_Subscription associated with the Customer_Session and its Tenant_Id within 5 seconds.
2. THE Subscription_Service SHALL store the Device_Subscription endpoint and keys required to send Web_Push messages.
3. IF a Device_Subscription registration request is missing the endpoint or the keys required to send Web_Push messages, THEN THE Subscription_Service SHALL reject the request, SHALL NOT store any partial Device_Subscription, and SHALL return an error response identifying the missing data.
4. WHEN a Customer grants Web_Push permission with an endpoint that already exists for the same Customer_Session, THE Subscription_Service SHALL update the existing Device_Subscription rather than create a duplicate.
5. WHEN a Customer revokes Web_Push permission or removes the Device_Subscription, THE Subscription_Service SHALL delete the corresponding Device_Subscription and SHALL direct no further Web_Push messages to it.
6. IF the NotificationSender_Port reports that a Device_Subscription endpoint is expired or invalid, THEN THE Subscription_Service SHALL delete the corresponding Device_Subscription and SHALL direct no further Web_Push messages to it.
7. THE Subscription_Service SHALL transmit Device_Subscription data over encrypted channels.
8. THE Subscription_Service SHALL store Device_Subscription data in encrypted-at-rest storage.

### Requirement 4: Order Creation and Association

**User Story:** As a Restaurant Staff member, I want to create orders linked to a customer session, so that customers can track the orders they placed.

#### Acceptance Criteria

1. WHEN a Restaurant_Staff member creates an Order for an active Customer_Session that belongs to the member's Restaurant, THE Order_Service SHALL create the Order in the CREATED state.
2. WHEN the Order_Service creates an Order, THE Order_Service SHALL assign the Tenant_Id of the creating Restaurant_Staff member's Restaurant to the Order.
3. WHEN the Order_Service has created an Order, THE Order_Service SHALL publish an OrderCreated Domain_Event.
4. WHEN the Order_Service creates an Order for a Customer_Session, THE Order_Service SHALL associate the Order with that Customer_Session.
5. IF a Restaurant_Staff member requests creation of an Order for a Customer_Session that does not exist or is not active, THEN THE Order_Service SHALL reject the request without creating an Order and return an error response identifying the Customer_Session as invalid.
6. IF a Restaurant_Staff member requests creation of an Order for a Customer_Session whose Tenant_Id differs from the Tenant_Id of the member's Restaurant, THEN THE Order_Service SHALL reject the request without creating an Order and return an error response identifying the Customer_Session as inaccessible.

### Requirement 5: Order Lifecycle Management

**User Story:** As a Restaurant Staff member, I want to update order status through defined stages, so that customers see accurate progress and get notified when their order is ready.

#### Acceptance Criteria

1. THE Order_Service SHALL permit Order state transitions only along the Order_Lifecycle: CREATED to IN_PROGRESS, IN_PROGRESS to READY, and READY to COMPLETED.
2. THE Order_Service SHALL permit an Order to transition to CANCELLED only from the CREATED, IN_PROGRESS, or READY state.
3. IF a Restaurant_Staff member requests an Order state transition whose source-state-to-target-state pair is not permitted by criteria 1 and 2, THEN THE Order_Service SHALL reject the transition, retain the Order in its current state without modification, and return an error response identifying the transition as invalid.
4. IF a Restaurant_Staff member requests a transition of an Order to the state it already occupies, THEN THE Order_Service SHALL reject the transition, retain the Order in its current state without modification, and return an error response identifying the transition as invalid.
5. WHEN the Order_Service changes an Order state, THE Order_Service SHALL publish an OrderStatusUpdated Domain_Event.
6. WHEN the Order_Service transitions an Order to the READY state, THE Order_Service SHALL publish an OrderReady Domain_Event.
7. WHERE an Order has reached the COMPLETED or CANCELLED state, THE Order_Service SHALL reject any further state transition for that Order, retain the Order in its current state without modification, and return an error response.
8. IF two or more state transitions are requested for the same Order concurrently, THEN THE Order_Service SHALL apply at most one transition and leave the Order in a single well-defined Order_Lifecycle state.

### Requirement 6: Order-Ready Notification Delivery

**User Story:** As a Customer, I want to receive a push notification when my order is ready, so that I can pick it up promptly without watching the screen.

#### Acceptance Criteria

1. WHEN an OrderReady Domain_Event is published, THE Notification_Service SHALL compose a Notification for each Device_Subscription associated with the Order's Customer_Session whose Tenant_Id matches the Order's Tenant_Id.
2. WHEN the Notification_Service composes a Notification, THE Notification_Service SHALL deliver the Notification through the NotificationSender_Port using Web_Push authenticated with VAPID.
3. WHEN an OrderReady Domain_Event is published, THE Notification_Service SHALL cause the NotificationSender_Port to accept the corresponding Web_Push message for each associated Device_Subscription within a Notification_Latency of 2 seconds.
4. WHEN the NotificationSender_Port accepts a Notification for delivery, THE Notification_Service SHALL publish a NotificationSent Domain_Event.
5. WHEN the NotificationSender_Port confirms delivery of a Notification, THE Notification_Service SHALL publish a NotificationDelivered Domain_Event.
6. IF no Device_Subscription is associated with the Order's Customer_Session, THEN THE Notification_Service SHALL record that no Notification was delivered and continue processing without error.
7. IF the NotificationSender_Port fails to accept a Notification or does not accept it within the Notification_Latency of 2 seconds, THEN THE Notification_Service SHALL record the failure for the affected Device_Subscription and continue processing the remaining Device_Subscriptions without error.
8. IF the NotificationSender_Port reports that delivery of a Notification failed, THEN THE Notification_Service SHALL record the delivery failure for the affected Device_Subscription and continue processing without error.

### Requirement 7: Restaurant Staff Panel

**User Story:** As a Restaurant Staff member, I want a web panel to view and manage active orders, so that I can keep order statuses current during service.

#### Acceptance Criteria

1. WHEN a Restaurant_Staff member submits valid credentials to the Restaurant_Panel, THE ReadyBell_Platform SHALL grant access scoped to the Tenant_Id of that member's Restaurant.
2. IF a Restaurant_Staff member's authentication attempt to the Restaurant_Panel fails, THEN THE ReadyBell_Platform SHALL deny access, establish no session, and return an error indication that authentication failed.
3. WHILE a Restaurant_Staff member is authenticated, THE Restaurant_Panel SHALL display the Orders belonging to that member's Restaurant with each Order's current Order_Lifecycle state.
4. WHEN the state of an Order belonging to the member's Restaurant changes, THE Restaurant_Panel SHALL update the displayed Order state within 10 seconds of the change.
5. THE Restaurant_Panel SHALL allow a Restaurant_Staff member to create an Order.
6. THE Restaurant_Panel SHALL allow a Restaurant_Staff member to change the state of an Order along the Order_Lifecycle.
7. IF a Restaurant_Staff member requests an Order that belongs to a different Tenant_Id, THEN THE Restaurant_Panel SHALL deny access to that Order, return an error indication, and not disclose the Order contents.

### Requirement 8: Restaurant Administration

**User Story:** As a Restaurant Admin, I want to manage staff accounts and restaurant configuration, so that my team can operate the platform for my restaurant.

#### Acceptance Criteria

1. WHEN a Restaurant_Admin successfully authenticates to the Restaurant_Panel, THE ReadyBell_Platform SHALL grant access scoped exclusively to records matching the Tenant_Id of that admin's Restaurant.
2. IF a Restaurant_Admin's authentication attempt to the Restaurant_Panel fails, THEN THE ReadyBell_Platform SHALL deny access, retain no active session, and return an error indication that authentication failed.
3. WHEN a Restaurant_Admin submits a request to create, update, or deactivate a Restaurant_Staff account within the admin's Restaurant, THE Restaurant_Panel SHALL apply the operation and associate the account with the admin's Tenant_Id.
4. IF a Restaurant_Admin submits a Restaurant_Staff account request with a missing required field or a duplicate account identifier within the same Tenant_Id, THEN THE Restaurant_Panel SHALL reject the request, leave existing account data unchanged, and return an error indication identifying the invalid field.
5. WHEN a Restaurant_Admin deactivates a Restaurant_Staff account, THE Restaurant_Panel SHALL terminate that account's active sessions and deny its subsequent access to the Restaurant_Panel.
6. WHEN a Restaurant_Admin submits valid configuration changes for the admin's Restaurant, THE Restaurant_Panel SHALL persist the configuration and scope it to the admin's Tenant_Id.
7. IF a Restaurant_Admin submits a configuration change that fails validation, THEN THE Restaurant_Panel SHALL reject the change, retain the previous configuration unchanged, and return an error indication identifying the invalid configuration value.
8. IF a Restaurant_Admin attempts to manage a Restaurant_Staff account or configuration belonging to a different Tenant_Id, THEN THE Restaurant_Panel SHALL deny the operation, leave the target records unchanged, and return an error indication that the operation is not permitted.

### Requirement 9: Platform Administration and Tenant Management

**User Story:** As a Platform Admin, I want to manage restaurants as tenants, so that I can onboard and configure restaurants across the platform.

#### Acceptance Criteria

1. WHEN a Platform_Admin authenticates to the ReadyBell_Platform, THE ReadyBell_Platform SHALL grant access to tenant management functions across all Restaurants.
2. WHEN a Platform_Admin creates a Restaurant with valid data, THE ReadyBell_Platform SHALL assign a unique Tenant_Id to the Restaurant.
3. THE ReadyBell_Platform SHALL allow a Platform_Admin to create, update, and deactivate Restaurants.
4. IF a user who is not a Platform_Admin requests a tenant management function, THEN THE ReadyBell_Platform SHALL deny the operation and return an error indication that the operation is not permitted.
5. IF a Platform_Admin submits a Restaurant creation request with a missing required field or a duplicate Restaurant identifier, THEN THE ReadyBell_Platform SHALL reject the request, SHALL NOT create a Restaurant, and SHALL return an error indication identifying the invalid field.
6. WHILE a Restaurant is deactivated, THE Session_Service SHALL reject new Customer_Session starts for that Restaurant and return an error response.

### Requirement 10: Multi-Tenant Data Isolation

**User Story:** As a Platform operator, I want strict tenant isolation, so that each restaurant only accesses its own data in the shared database.

#### Acceptance Criteria

1. THE ReadyBell_Platform SHALL persist Customer_Session, Order, Notification, and Device_Subscription records with the Tenant_Id of their owning Restaurant.
2. WHEN a data query is executed for a request bound to a Tenant_Id, THE Tenant_Context_Filter SHALL constrain the query results to only records whose Tenant_Id is equal to the request's Tenant_Id.
3. IF a read, update, or delete operation targets a record whose Tenant_Id differs from the request's Tenant_Id, THEN THE Tenant_Context_Filter SHALL exclude that record from the results and SHALL leave the record unmodified.
4. THE Tenant_Context_Filter SHALL apply the Tenant_Id constraint at the repository query layer for every tenant-scoped data access.
5. IF a tenant-scoped request has no resolvable Tenant_Id, THEN THE Tenant_Context_Filter SHALL reject the request, return zero records, and produce an error indicating that the tenant context is missing.
6. WHEN a create operation persists a tenant-scoped Customer_Session, Order, Notification, or Device_Subscription record, THE ReadyBell_Platform SHALL assign the request's Tenant_Id to that record before it is committed.

### Requirement 11: Domain Event Publication Through Ports

**User Story:** As a platform engineer, I want domain events published behind hexagonal ports, so that the in-process event mechanism can be replaced with an external broker without changing the domain.

#### Acceptance Criteria

1. THE ReadyBell_Platform SHALL publish each Domain_Event through a hexagonal outbound domain event port rather than a concrete messaging implementation.
2. THE domain model SHALL reference only the domain event port interface and SHALL NOT reference any concrete messaging implementation.
3. THE ReadyBell_Platform SHALL publish the CustomerSessionStarted, OrderCreated, OrderStatusUpdated, OrderReady, NotificationSent, and NotificationDelivered Domain_Events through the domain event port.
4. IF the domain event port fails to accept a Domain_Event for publication, THEN THE ReadyBell_Platform SHALL record the publication failure, return an error response identifying the affected Domain_Event, and retain the Domain_Event without discarding it.
5. WHERE the domain event port implementation is replaced with an alternative implementation, THE ReadyBell_Platform SHALL continue to publish every Domain_Event listed in criterion 3 without any modification to the domain model source.

### Requirement 12: Availability and Scalability

**User Story:** As a Platform operator, I want the platform to remain available and scale horizontally, so that it serves many restaurants and customers reliably.

#### Acceptance Criteria

1. THE ReadyBell_Platform SHALL operate as stateless application instances that can run as at least 2 concurrent replicas behind a load balancer.
2. WHERE two or more application replicas are available, THE ReadyBell_Platform SHALL route each incoming request to exactly one available replica and SHALL NOT route any request to an unavailable replica.
3. WHILE one application replica is unavailable, THE ReadyBell_Platform SHALL continue to serve incoming requests through the remaining available replicas without returning an error that is caused by the unavailability of that replica.
4. IF an application replica becomes unavailable, THEN THE ReadyBell_Platform SHALL stop routing new requests to that replica within 30 seconds of the replica failing its health check.
5. THE ReadyBell_Platform SHALL externalize session and cache state to a shared store so that any available replica can serve any request associated with an active Customer_Session.
