# ReadyBell Domain Model & Bounded Contexts v1.0

**Document Type:** Domain-Driven Design Specification
**Product:** ReadyBell
**Version:** 1.0
**Audience:** Architecture Team, Engineering Team, AI Development Agents (Kiro)
**Architecture Style:** Domain-Driven Design + Clean Architecture
**Status:** Foundation Document

---

# 1. Purpose

This document defines the core domain model of ReadyBell.

The goal is to establish:

* Business boundaries
* Domain concepts
* Aggregates
* Entities
* Value Objects
* Domain Events
* Module responsibilities

The implementation must follow this domain model.

The database schema, APIs, and technical infrastructure must be derived from this model.

---

# 2. Domain Vision

ReadyBell connects restaurants and guests through real-time digital communication.

The core business capability:

> Notify the right customer at the right moment when an order is ready.

The system evolves from notification into customer engagement.

---

# 3. Strategic Domain

The ReadyBell strategic domain is:

## Customer Engagement in Hospitality

The system creates a digital relationship layer between:

Restaurant → Customer → Experience

---

# 4. Bounded Context Overview

ReadyBell consists of the following bounded contexts:

```
+------------------------------------------------+
|                ReadyBell Platform                |
+------------------------------------------------+

 Restaurant Management Context

        |

 Order Communication Context

        |

 Notification Context

        |

 Customer Engagement Context

        |

 Analytics Context

```

---

# 5. Bounded Contexts

---

# 5.1 Restaurant Management Context

## Purpose

Manage restaurants, locations, users, and configuration.

---

## Responsibilities

Owns:

* Restaurant identity
* Locations
* Staff accounts
* Permissions
* QR configuration

---

## Core Entities

### Restaurant

Represents a ReadyBell customer tenant.

Attributes:

* RestaurantId
* Name
* Status
* CreatedAt

---

### Location

Represents a physical restaurant branch.

Attributes:

* LocationId
* Address
* Timezone

---

### StaffMember

Represents restaurant employees.

Attributes:

* StaffId
* Role
* Permissions

---

## Domain Events

* RestaurantCreated
* LocationCreated
* StaffMemberAdded

---

# 5.2 Order Communication Context

## Purpose

Manage customer order tracking lifecycle.

This is the core MVP domain.

---

## Aggregate Root

# OrderSession

The central business aggregate.

Represents:

The relationship between customer and restaurant order during preparation.

---

## OrderSession Responsibilities

Controls:

* Order identity
* Current status
* Customer tracking state
* Preparation progress

---

## Entities

### OrderSession

Attributes:

* OrderSessionId
* RestaurantId
* LocationId
* OrderReference
* Status
* EstimatedReadyTime
* CreatedAt

---

## Value Objects

### OrderReference

External identifier visible to customer.

Example:

"A125"

---

### PreparationEstimate

Represents expected completion time.

Contains:

* EstimatedMinutes
* UpdatedAt

---

### SessionIdentifier

Anonymous customer tracking identifier.

---

# Order Status Model

Allowed transitions:

```
CREATED
   |
PREPARING
   |
READY
   |
COMPLETED
```

Alternative:

```
CREATED
   |
CANCELLED
```

Invalid transitions must be rejected.

---

# Domain Commands

Examples:

```
CreateOrderSession

StartPreparation

UpdatePreparationEstimate

MarkOrderReady

CompleteOrder

CancelOrder

```

---

# Domain Events

The aggregate publishes:

## OrderSessionCreated

When:

New customer tracking session starts.

---

## OrderPreparationStarted

When:

Restaurant starts preparing order.

---

## PreparationEstimateUpdated

When:

Expected completion time changes.

---

## OrderReady

When:

Order is available for pickup.

---

## OrderCompleted

When:

Customer receives order.

---

# 5.3 Notification Context

## Purpose

Deliver customer communications.

Important:

Notification is NOT the source of business truth.

It reacts to domain events.

---

## Responsibilities

* Manage subscriptions
* Deliver push notifications
* Track delivery status

---

## Entities

### NotificationSubscription

Represents customer device permission.

Attributes:

* SubscriptionId
* SessionId
* DeviceType
* Endpoint

---

### NotificationMessage

Represents outgoing message.

Attributes:

* MessageId
* Type
* Content
* Status

---

## Domain Events

Consumes:

* OrderReady
* PreparationEstimateUpdated

Produces:

* NotificationSent
* NotificationFailed

---

# 5.4 Customer Engagement Context (Future)

## Purpose

Build long-term customer relationship.

Not part of MVP.

---

## Future Responsibilities

* Customer profiles
* Loyalty points
* Rewards
* Promotions
* Membership

---

## Future Entities

Customer

LoyaltyAccount

Reward

Campaign

---

# 5.5 Analytics Context (Future)

## Purpose

Business insights.

---

Responsibilities:

* Usage analytics
* Restaurant performance
* Customer behavior

---

# 6. Context Relationships

## Restaurant Management → Order Communication

Provides:

* Restaurant identity
* Permissions
* Configuration

---

## Order Communication → Notification

Publishes:

* OrderReady
* StatusChanged

---

## Notification → Customer Engagement

Future:

Notification interactions can trigger:

* Loyalty actions
* Marketing automation

---

# 7. Aggregate Rules

## OrderSession Aggregate

Must protect:

* Status consistency
* Valid transitions
* Business invariants

---

## Invariants

### Rule 1

Order cannot become READY before PREPARING.

---

### Rule 2

Completed orders cannot change status.

---

### Rule 3

Notifications cannot change order state.

---

# 8. Domain Events Catalog

| Event             | Producer           | Consumer     |
| ----------------- | ------------------ | ------------ |
| RestaurantCreated | Restaurant Context | Analytics    |
| OrderCreated      | Order Context      | Notification |
| OrderReady        | Order Context      | Notification |
| NotificationSent  | Notification       | Analytics    |
| CustomerReturned  | Engagement         | Marketing    |

---

# 9. Module Structure Recommendation

Initial implementation:

Modular Monolith.

Suggested modules:

```
readybell

 ├── restaurant
 │    ├── domain
 │    ├── application
 │    └── infrastructure

 ├── order
 │    ├── domain
 │    ├── application
 │    └── infrastructure

 ├── notification
 │    ├── domain
 │    ├── application
 │    └── infrastructure

 └── shared
      ├── events
      └── security

```

---

# 10. Architecture Rules

Kiro MUST:

* Keep bounded contexts isolated
* Avoid shared database logic between domains
* Communicate through contracts/events
* Protect aggregate boundaries

---

Kiro MUST NOT:

* Create one giant Order entity
* Put notification logic inside Order
* Couple restaurant and customer modules
* Design database-first

---

# 11. Evolution Path

## MVP

Contexts implemented:

✅ Restaurant Management
✅ Order Communication
✅ Notification

---

## Phase 2

Add:

Customer Engagement

---

## Phase 3

Add:

Analytics Platform

---

# 12. Final Domain Statement

The heart of ReadyBell is not notifications.

The heart of ReadyBell is the digital relationship created between a restaurant and a guest during a service moment.

The OrderSession is the bridge between operational workflow and customer experience.

---

**End of Document**
