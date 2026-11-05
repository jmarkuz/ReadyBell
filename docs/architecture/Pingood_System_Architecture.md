# Pingood System Architecture v1.0

**Document Type:** System Architecture Specification
**Product:** Pingood
**Version:** 1.0
**Audience:** Engineering Team, Solution Architects, AI Development Agents (Kiro)
**Architecture Style:** Cloud-Native Modular Monolith + Hexagonal Architecture
**Primary Technology:** Java + Spring Boot
**Status:** Technical Foundation Document

---

# 1. Architecture Vision

Pingood is designed as a scalable SaaS platform that connects restaurants and customers through real-time digital communication.

The architecture must support:

* Fast MVP delivery
* Multi-tenant SaaS operation
* Future platform expansion
* Event-driven evolution
* Independent domain growth

---

# 2. Core Architecture Principles

## Business First

Business capabilities define system boundaries.

Technology decisions must support domain requirements.

---

## Domain Driven Design

The system is organized around bounded contexts:

* Restaurant Management
* Order Communication
* Notification
* Future Customer Engagement

---

## Modular Monolith First

Initial architecture:

```
Single deployable application
+
Strict internal module boundaries
```

Reason:

* Faster development
* Lower operational complexity
* Future extraction capability

---

## Event Driven Communication

Business events are the main communication mechanism.

Example:

```
OrderReady
      |
      +----> Notification Service
      |
      +----> Analytics
      |
      +----> Loyalty (future)
```

---

# 3. High-Level Architecture

```
                    Users

        Customer PWA        Restaurant Dashboard

               |
               |
          API Gateway Layer

               |

        Spring Boot Application

+------------------------------------------------+

 Restaurant Module

 Order Module

 Notification Module

 Shared Kernel

 Event Processing

+------------------------------------------------+

               |

 Infrastructure Layer

 PostgreSQL
 Redis
 Message Broker

+------------------------------------------------+

               |

 Cloud Infrastructure

 Docker
 Kubernetes
 CI/CD

```

---

# 4. Application Architecture

## Hexagonal Architecture

Each module follows:

```
module

├── domain
│
│   Entities
│   Value Objects
│   Domain Events
│
├── application
│
│   Use Cases
│   Commands
│   Services
│
├── adapters
│
│   REST Controllers
│   Message Consumers
│
└── infrastructure
    Database
    External Services

```

---

# 5. Spring Boot Module Structure

Recommended project structure:

```
pingood-platform

├── pingood-api

├── pingood-restaurant

│   ├── domain
│   ├── application
│   ├── adapters
│   └── infrastructure


├── pingood-order

│   ├── domain
│   ├── application
│   ├── adapters
│   └── infrastructure


├── pingood-notification

│   ├── domain
│   ├── application
│   ├── adapters
│   └── infrastructure


├── pingood-security

├── pingood-events

└── pingood-shared

```

---

# 6. Module Responsibilities

# Restaurant Module

Owns:

* Restaurant tenant
* Locations
* Staff users
* Permissions

Does NOT own:

* Orders
* Notifications

---

# Order Module

Core business module.

Owns:

* OrderSession
* Status lifecycle
* Preparation estimate

Produces:

* OrderCreated
* OrderReady

---

# Notification Module

Owns:

* Push subscriptions
* Notification delivery
* Delivery tracking

Consumes:

* OrderReady

---

# Security Module

Owns:

* Authentication
* Authorization
* Tenant context

---

# 7. REST API Boundaries

REST APIs expose business capabilities.

Do NOT expose database entities directly.

---

# Customer APIs

Example:

```
GET /api/customer/sessions/{sessionId}

```

Purpose:

Retrieve order status.

---

```
POST /api/customer/subscriptions

```

Purpose:

Register push notification subscription.

---

# Restaurant APIs

Example:

```
POST /api/restaurants/{id}/orders

```

Create order session.

---

```
PATCH /api/orders/{id}/status

```

Update order lifecycle.

---

# Admin APIs

Example:

```
POST /api/restaurants

```

Create tenant.

---

# API Principles

Required:

* RESTful design
* OpenAPI documentation
* Versioning

Example:

```
/api/v1/orders
```

---

# 8. Event Infrastructure

## Purpose

Separate business actions from reactions.

---

# MVP Approach

Use internal domain event bus.

Future:

Kafka/RabbitMQ.

---

# Event Flow Example

```
Staff clicks READY

        |

Order Aggregate

        |

OrderReady Event

        |

Event Handler

        |

Notification Service

        |

Push Provider

```

---

# Event Requirements

Every event must contain:

```
eventId
eventType
aggregateId
tenantId
timestamp
payload

```

---

# 9. PostgreSQL Strategy

## Database

PostgreSQL

Reason:

* Reliable relational model
* Strong consistency
* Excellent Spring support

---

# Multi-Tenant Strategy

MVP:

Shared database with tenant isolation.

Every business table contains:

```
tenant_id
```

Example:

```
orders

id
tenant_id
status
created_at

```

---

# Future Options

If required:

* Schema-per-tenant
* Database-per-tenant

---

# Migration

Use:

* Flyway

Requirements:

* Version controlled migrations
* Automated execution

---

# 10. Redis Usage

Redis is NOT the source of truth.

Used for:

## Caching

Examples:

* Restaurant configuration
* Frequently accessed sessions

---

## Temporary Data

Examples:

* Push subscription cache
* Rate limiting

---

## Real-Time Support

Future:

* WebSocket state
* Live updates

---

# 11. Notification Architecture

Notification flow:

```
OrderReady Event

        |

Notification Handler

        |

Notification Queue

        |

Push Provider

        |

Customer Device

```

---

Requirements:

* Retry mechanism
* Failure handling
* Delivery tracking

---

# 12. Security Model

## Authentication

Restaurant users:

* JWT based authentication

---

## Authorization

Role model:

```
PLATFORM_ADMIN

RESTAURANT_ADMIN

STAFF

CUSTOMER_SESSION

```

---

## Tenant Isolation

Every request must contain:

```
tenantId

```

Validation required at application layer.

---

## Security Requirements

Must implement:

* Password hashing
* HTTPS only
* Secure tokens
* Input validation
* Rate limiting

---

# 13. Docker Architecture

Application container:

```
pingood-app

```

Dependencies:

```
postgres
redis
message-broker

```

Local development:

Docker Compose.

---

# 14. Kubernetes Readiness

Architecture should support:

* Stateless application instances
* Horizontal scaling
* Environment configuration
* Health checks

Required endpoints:

```
/actuator/health

```

---

# 15. CI/CD Pipeline

Recommended flow:

```
Developer

   |

Git Repository

   |

Build

   |

Automated Tests

   |

Docker Image

   |

Security Scan

   |

Deployment

```

---

# Pipeline Requirements

Every merge requires:

* Compilation
* Unit tests
* Integration tests
* Static analysis

---

# 16. Observability

Required:

## Logging

Structured JSON logs.

---

## Metrics

Track:

* API latency
* Notification delivery time
* Errors
* Active tenants

---

## Monitoring

Future compatible with:

* Prometheus
* Grafana
* OpenTelemetry

---

# 17. AI Agent Implementation Rules

Kiro MUST:

* Follow module boundaries
* Keep domain logic independent
* Use dependency inversion
* Write tests before completing features

---

Kiro MUST NOT:

* Create microservices prematurely
* Put business logic in controllers
* Couple modules through database tables
* Skip domain events

---

# 18. Evolution Strategy

## MVP

Modular monolith.

---

## Growth Phase

Extract independent services:

Possible candidates:

* Notification Service
* Analytics Service
* Loyalty Service

---

# 19. Final Architecture Statement

Pingood starts as a modular Spring Boot SaaS application.

The architecture is intentionally designed to evolve into a global hospitality engagement platform.

The system must optimize for:

* Business agility
* Customer experience
* Operational simplicity
* Long-term scalability

---

**End of Document**


## Transactional outbox (T05)

`OrderReady` and `OrderStatusUpdated` are not published directly. `ManageOrderService` runs inside one transaction
(`TransactionalManageOrderUseCase`); the `OutboxDomainEventPublisher` adapter of the `DomainEventPublisher` port inserts the
events into `outbox_event` in that same transaction, so a status change and its events commit or roll back together.
`OutboxRelay` (platform) then delivers them to the existing in-process handlers:

1. `claim`: select due, unprocessed rows `FOR UPDATE SKIP LOCKED`, set `locked_until` (lease), commit. Several nodes can
   run the relay; each event goes to one of them. A worker that dies lets the lease expire and the event is claimed again.
2. dispatch to the handlers synchronously on the relay thread; success sets `processed_at`, an exception schedules a retry
   with exponential backoff (2 s doubling to 5 min) and after `max-attempts` (10) parks the row (`failed_at`).
3. delivery is **at least once**: `OrderReadyHandler` skips a device that already has a SENT/DELIVERED notification for the
   order, so a replay does not push twice. A crash in the instant between the push service accepting a message and the
   notification being saved as SENT can still repeat that one push.

A commit wakes the relay immediately (live updates stay within a second); a 1 s poll covers restarts and missed wake-ups.
Config: `readybell.outbox.*` (`enabled`, `poll-interval`, `batch-size`, `lease`, `max-attempts`, `retention`). Metrics:
`outbox.pending`, `outbox.failed`. Processed rows are deleted after 7 days.
