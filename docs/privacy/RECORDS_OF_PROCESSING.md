# Records of processing (draft, GDPR art. 30)

Roles: the **restaurant** is the controller of its customers' and staff's data; **ReadyBell** is the processor. For the
restaurant's own contact and the platform operator account ReadyBell is the controller. Not legal advice.

## What is processed, why, where, how long

| Data | Whose | Purpose | Where | Kept | Removed by |
|---|---|---|---|---|---|
| Customer name (optional, free text, a first name or initials is enough) | Customer | tell staff who an order is for | `order.customer_name` | `readybell.retention.order-days` after the order is finished (default 30, 7-365) | retention run `anonymize-orders`; at once on the customer's request |
| Item names (free text) | Customer | prepare the order | `order_item.name` | as the name | as the name |
| Link to the customer (random session id), order times and status | Customer (pseudonymous) | order tracking page, hand-over | `order.session_id`, `customer_session` | session ends 24 h after start; link to a finished order is replaced by a random id after `order-days`; ended sessions deleted after `session-days` (30) | `anonymize-orders`, `purge-sessions`, customer request |
| Web Push subscription (address of the device at the push service and its keys) | Customer who pressed "Notify me" | send the "ready" notification | `device_subscription` | until the session ends, the push service reports the device gone, or the customer erases | housekeeping, delivery worker, customer request |
| Consent to the push (time, notice version) | Customer | proof of consent | `device_subscription.consent_at`, `consent_notice` | with the subscription | with the subscription |
| Text of a sent notification ("Order #12 is ready") | Customer | delivery record | `notification.payload` | `notification-days` (30); status and times stay | `scrub-notifications` |
| Staff username, password hash, role, status | Staff | sign-in, access control | `staff_account` | while employed; see open items | restaurant admin (deactivate); deletion workflow is an open item |
| Restaurant name, identifier | Restaurant | tenant | `restaurant` | while a customer of the platform | offboarding (open item) |
| Order timings, outcome, size, whether the link was opened (no name, items, or link to a person) | none (anonymous facts) | operational reports, analytics | `order_fact`, `order_status_history` | long-term | not personal data |
| IP address of the caller, username (failed sign-ins) | Customer, staff | rate limits, login throttling | Redis keys with a time to live | minutes to a day | expiry |
| Revoked token ids, single-use stream tickets | Staff | session revocation | Redis keys with a time to live | token lifetime / 30 s | expiry |
| Application log: first 8 characters of a session id, push service host | Customer | operations | log files | log retention of the host (set to 30 days) | log rotation |
| Backups of the database | all of the above | recovery | `pg_dump` files | 14 days by default | rotation; see the runbook for restores |
| Proof of erasure (keyed hash of the session id, time) | Customer | prove and repeat erasures | `erasure_log` | long-term (it holds nothing personal) | - |

## Recipients and sub-processors (to be confirmed with the real setup)

| Who | What they get | Note |
|---|---|---|
| Push services: Google (FCM), Mozilla, Apple, Microsoft | the notification text and the device address | outside the EU for some of them: a transfer mechanism is needed (standard contractual clauses) |
| Hosting provider of the application, database and backups | everything above | contract and encryption at rest |
| Restaurant staff | customer name and items of the restaurant's own orders only (tenant isolation) | |

## Technical and organisational measures (summary)

Tenant isolation by a database filter bound in the persistence layer; HTTPS only; hashed passwords (BCrypt); token revocation;
rate limits; SSRF protection of the push address; dependency and image scanning in CI; encrypted backups are an operator duty;
audit of operator actions is an open item.

## Open items

Staff account deletion (`anonymize` after deactivation), restaurant offboarding and export, per-restaurant retention settings,
the signed processing agreement, the final privacy notice, a retention period for facts that outlives a restaurant's contract.
