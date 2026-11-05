# Pilot readiness checklist

Field checklist for the first pilot restaurants. It covers BACKLOG items **P1** (Web Push on real devices) and
**P2** (real host, TLS, stable VAPID pair, platform operator), and turns the MVP success criteria
([Pingood_MVP_Definition.md](product/Pingood_MVP_Definition.md), section 9) into measurable pass/fail checks.
P3 (backups) and P4 (counter QR flow) are separate backlog items, see [section 5](#5-known-limitations).

Related: [DEPLOYMENT.md](DEPLOYMENT.md), [API_INTEGRATION.md](API_INTEGRATION.md), [BACKLOG.md](BACKLOG.md).

Tick boxes in a copy of this file (or a printout) per environment. Anything unticked blocks the pilot.

## 1. Pre-flight (P2)

### 1.1 Host and TLS

- [ ] A host with a public DNS name and a valid TLS certificate (reverse proxy or load balancer in front of the app on port 8080; `APP_PORT` changes the published port).
- [ ] `https://<host>/actuator/health` returns `{"status":"UP"}` from a phone on mobile data (not only from the office network).
- [ ] The proxy forwards `X-Forwarded-For` and `RATE_LIMIT_TRUSTED_PROXIES` lists the proxy IP/CIDR. Without it every client shares one rate-limit budget (20 session starts/min), which would throttle a busy restaurant.
- [ ] Liveness/readiness probes wired to `/actuator/health/liveness` and `/actuator/health/readiness`.
- [ ] `/actuator/prometheus` is **not** reachable without credentials (platform operator only).

### 1.2 Environment variables

Copy `.env.example` to `.env` (never commit it). `docker-compose.yml` refuses to start without `DB_PASSWORD`, `JWT_SECRET`, `PLATFORM_ADMIN_USERNAME` and `PLATFORM_ADMIN_PASSWORD_HASH`.

| Variable | Required | How to produce / check |
|---|---|---|
| `DB_PASSWORD` | yes | Any strong password. |
| `JWT_SECRET` | yes | `openssl rand -base64 48` (at least 32 characters). Changing it signs everybody out. |
| `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY` | yes for push | `python3 scripts/generate-vapid-keys.py` (needs the Python `cryptography` package). Run it **once**, store the pair in your secret store and reuse it on every deploy: changing it invalidates every customer subscription. Without real keys compose falls back to placeholders, `/api/push/config` reports `enabled: false` and the customer page hides "Notify me". |
| `PLATFORM_ADMIN_USERNAME` | yes | e.g. `platform`. |
| `PLATFORM_ADMIN_PASSWORD_HASH` | yes | bcrypt hash with the `{bcrypt}` prefix and every `$` doubled to `$$` (Compose would otherwise mangle it). Command from `.env.example`: `echo "{bcrypt}$(htpasswd -bnBC 10 "" 'your-password' \| tr -d ':\n' \| sed 's/$2y/$2a/; s/\$/$$/g')"` (`htpasswd` is in `apache2-utils` / `httpd-tools`; the `\|` is only table escaping, type a plain `|` in the shell). Use a long random password. |
| `RATE_LIMIT_TRUSTED_PROXIES` | behind a proxy | IP or CIDR list, e.g. `10.0.0.0/8`. |
| `CORS_ALLOWED_ORIGINS` | only if the UI is on another origin | Leave empty for the single container. |

- [ ] `.env` filled in, VAPID pair saved outside the host (password manager / secret store).
- [ ] `docker compose up --build -d`, all three containers healthy (`docker compose ps`).
- [ ] Startup log shows `VAPID Web Push service initialized successfully.` (and not `VAPID credentials are not configured`).
- [ ] `curl -s https://<host>/api/push/config` returns `"enabled":true` and a `publicKey` equal to `VAPID_PUBLIC_KEY`.
- [ ] Decide on the VAPID contact: the subject defaults to `mailto:admin@readybell.example.com` (`readybell.vapid.subject` in `application.yml`). Spring relaxed binding should accept `READYBELL_VAPID_SUBJECT`, but `docker-compose.yml` does not pass it through today, and whether any push service rejects the placeholder domain was not verified.

### 1.3 Smoke test

```bash
BASE_URL=https://<host> PLATFORM_USER=<PLATFORM_ADMIN_USERNAME> PLATFORM_PASS='<plain password>' scripts/smoke.sh
```

It checks health, operator login, restaurant + admin creation, admin login, order creation, the public tracking link,
`IN_PROGRESS -> READY`, and prints `enabled=True|False` for push.

- [ ] Ends with `OK: smoke test passed (smoke-xxxxxxxx)` and prints `enabled=True`.
- [ ] The throwaway restaurant `smoke-xxxxxxxx` was deactivated afterwards (platform operator, **Restaurants**, Deactivate). The script does not do this, and it leaves one order and one session behind.

Sign-in is throttled after repeated failures (429): a wrong `PLATFORM_PASS` tried several times locks you out for a while.

## 2. Restaurant onboarding and timing

MVP criteria: onboarding < 5 minutes, create order < 10 s, customer session < 10 s.

### 2.1 Steps

Platform operator (role `PLATFORM_ADMIN`):

1. [ ] Open `https://<host>/login`, sign in with `PLATFORM_ADMIN_USERNAME` and the plain password. The sidebar shows **Restaurants**.
2. [ ] **Restaurants**, form "add restaurant": **Name**, **Identifier** (e.g. `cafe-aurora`; this is the value the session API takes as `qrToken`), **First admin username**, **Admin password** (min. 8 characters). Press **Add restaurant**.
   The admin fields are optional but must be given together; leave them out only if the account is created another way.
3. [ ] The restaurant appears in the list. (Deactivating it later refuses new customer sessions.)

Restaurant admin (role `RESTAURANT_ADMIN`):

4. [ ] Sign in as the new admin on a staff device (tablet/phone/PC at the counter).
5. [ ] **Staff**: add an account per cashier: **Username**, **Password** (min. 8 characters), **Role** (`STAFF`; `RESTAURANT_ADMIN` for managers). Press **Add account**.
6. [ ] **Settings**: check or rename the restaurant if needed.
7. [ ] Sign out, sign in as a `STAFF` account on the cashier device.

First order (staff):

8. [ ] Dashboard, **New order**: optional **Customer name**, items, estimated minutes, **Create order**. The dialog shows the receipt QR code with **Print**. Print or display it.
9. [ ] Scan the QR with a customer phone: the tracking page `/track/<sessionId>` opens and shows the order.
10. [ ] Set the status from the orders table (pencil menu): *Preparing* (`IN_PROGRESS`), then `READY`. The customer page updates live.

### 2.2 Timing sheet

Use a stopwatch (phone). One row per measurement. The app records no timings, so the stopwatch is the source of truth.

| # | Restaurant | Date | Measurement | Start | Stop | Seconds | Limit (s) | Pass |
|---|---|---|---|---|---|---|---|---|
| 1 | | | Onboarding: restaurant created, admin created, one staff created, first order created and visible on a customer phone | Operator opens **Restaurants** | Customer phone shows the order | | 300 | [ ] |
| 2 | | | Create order (1 item, no name) | Tap **New order** | QR code shown | | 10 | [ ] |
| 3 | | | Customer session | Camera opens the QR | Tracking page shows the order | | 10 | [ ] |
| 4 | | | Mark ready is one action | Menu open | Row shows Ready | | one action | [ ] |

Repeat rows 2 and 3 at least 5 times per restaurant and take the median; note the network (Wi-Fi / mobile) in the remarks.

Remarks:

## 3. Real-device Web Push matrix (P1)

Needs the HTTPS host from section 1 with the real VAPID pair. Web Push does not work on plain `http://` (localhost excepted).
Use a **new** order for each run.

### 3.1 Procedure per device

1. Staff creates an order and shows/prints the QR.
2. Customer scans it. Browser-specific preconditions are in the matrix below.
3. On the tracking page tap **Notify me** (Ukrainian UI: *Сповістити мене*) and allow notifications. The page then reads "Notifications are on. You can close this page."
4. Close the page / lock the screen.
5. Staff sets the order to **Preparing**, then starts a stopwatch and sets **Ready**. Stop the stopwatch when the notification appears on the device.
6. Tap the notification: it must open `/track/<sessionId>` (it focuses an existing tab at that URL, otherwise opens a new window) showing **Ready**.

### 3.2 Matrix

Fill in OS/browser versions. Pass: subscribes, notification within 2 s, tap opens the tracking page.

| Device / browser | Precondition | Subscribe ok | READY ping seen | Delay (s), < 2 | Tap opens `/track/…` | Works with page closed | Pass |
|---|---|---|---|---|---|---|---|
| Android Chrome (version: ) | Open the link in Chrome | [ ] | [ ] | | [ ] | [ ] | [ ] |
| Android Chrome, battery saver on | As above | [ ] | [ ] | | [ ] | [ ] | [ ] |
| iPhone Safari 16.4+ (iOS: ), **installed to Home Screen** | Share, "Add to Home Screen", open the Home Screen icon (not a Safari tab), then tap **Notify me** | [ ] | [ ] | | [ ] | [ ] | [ ] |
| iPhone Safari tab, **not installed** (negative test) | Page shows the iOS hint | n/a | n/a | n/a | n/a | n/a | [ ] hint shown |
| Desktop Chrome (version: ) | Any OS | [ ] | [ ] | | [ ] | [ ] | [ ] |
| Desktop Firefox (version: ) | Any OS | [ ] | [ ] | | [ ] | [ ] | [ ] |
| Permission denied (negative test) | Block notifications, tap **Notify me** | n/a | n/a | n/a | n/a | n/a | [ ] denied message shown, tracking still works |

The iOS hint on the page: *On iPhone: tap Share -> "Add to Home Screen", open the app from there, then tap "Notify me".*

### 3.3 Expired endpoint is removed

The server deletes a subscription when the push service answers HTTP 404 or 410 (`ENDPOINT_EXPIRED`).

- [ ] Subscribe a device (as above), then invalidate the endpoint: uninstall the PWA / reset the site's notification permission / clear site data.
- [ ] Set the order to **Ready**.
- [ ] Logs show `Endpoint expired for subscription <id>; deleting subscription.`
- [ ] The subscription is gone: `docker compose exec postgres psql -U readybell readybell -c "select id from device_subscription where session_id = '<sessionId>'"` returns no row.
- [ ] The `notification` row for it has `status = 'FAILED'`.

Browsers do not always revoke the endpoint at once and some push services keep accepting for a while. If you get no 404/410, record that and retry later rather than failing the check.

### 3.4 Reading the result from logs, database and metrics

| What | Where | Notes |
|---|---|---|
| No subscribers | log `OrderReady [orderId=…, sessionId=…, tenantId=…]: no device subscriptions — no notification delivered.` | Customer never tapped **Notify me**. |
| Duplicate suppressed | log `OrderReady <id>: push to subscription <id> already sent, skipping.` | Idempotency on outbox redelivery. |
| Expired endpoint | WARNING `Endpoint expired for subscription <id>; deleting subscription.` | Subscription deleted. |
| Rejected by push service | WARNING `Notification rejected for subscription <id>; recording failure.`, `Unexpected push service HTTP status: <n>` | 4xx other than 404/410 (typically bad VAPID keys). |
| Transient failure | WARNING `Web Push delivery failed for endpoint …`, `Push service temporarily unavailable (HTTP <n>)` | I/O error or 5xx. |
| VAPID unusable | `VAPID credentials are not configured…` / `Failed to initialize VAPID Web Push service…` | Fix the keys. |
| Outcome per push | table `notification`, columns `status` (`PENDING`, `SENT`, `DELIVERED`, `FAILED`), `created_at` | `select status, count(*) from notification where created_at > now() - interval '1 day' group by 1;` `SENT` means handed to the push service, not shown on the phone. |
| Stuck events | table `outbox_event` (`processed_at`, `failed_at`, `attempts`, `last_error`) | `select count(*) from outbox_event where processed_at is null and failed_at is null;` should stay near 0 (relay polls every 1 s and retries with backoff). |
| Rate limiting | `/actuator/metrics/ratelimit.rejected` (tag `endpoint`) | Needs operator credentials. |
| General | `/actuator/prometheus` (`http_server_requests_*`, JVM), HTTP Basic with the platform operator for scrapers | There are **no custom push counters yet** (BACKLOG R2); push success/failure comes from logs and the `notification` table. |

Delivery latency has no server-side metric. The 2 s criterion is measured with the stopwatch in 3.1 (tap READY to notification visible). On a failed run, note the time of READY and compare with the log lines above to see whether the delay was before or after the server handed the message to the push service.

## 4. Pilot run sheet

### 4.1 Before opening each restaurant

- [ ] Section 1 done on production; section 2 timing rows 1-3 pass.
- [ ] At least one device per platform in the restaurant's expected customer mix passed section 3.
- [ ] Staff know that each new order shows a fresh QR (**New order** dialog, **Print**) to print on the receipt.
- [ ] Staff have a fallback (physical pager / calling the number) if the dashboard is offline.
- [ ] Contacts below are filled in.

### 4.2 Daily log (one row per restaurant per day)

Customer sessions per restaurant and day:
`select count(*) from customer_session s join restaurant r on r.tenant_id = s.tenant_id where r.identifier = '<identifier>' and s.started_at::date = current_date;`
Notifications: the `notification` table as in 3.4 (it has `tenant_id`). Complaints come from staff and customers.

| Date | Restaurant | Orders | Customer sessions | Subscribed ("Notify me") | Pushes SENT | Pushes FAILED | Median delay (s) | Complaints / issues | Pager still used (count) | Initials |
|---|---|---|---|---|---|---|---|---|---|---|
| | | | | | | | | | | |
| | | | | | | | | | | |
| | | | | | | | | | | |

"Pager still used" is how the pilot measures the MVP goal of reducing physical pager usage: ask staff to tally it and compare with the pre-pilot baseline (record it here: ).

### 4.3 Success criteria

| Criterion | Target | Measured | Pass | Fail |
|---|---|---|---|---|
| Pilot restaurants onboarded | 5-10 | | [ ] | [ ] |
| Total customer sessions | 500+ | | [ ] | [ ] |
| Restaurant onboarding time | < 5 min (median) | | [ ] | [ ] |
| Staff create an order | < 10 s (median) | | [ ] | [ ] |
| Staff mark order ready | one action | | [ ] | [ ] |
| Customer starts a session | < 10 s (median) | | [ ] | [ ] |
| Push permission flow works | Works on every device in 3.2 | | [ ] | [ ] |
| Notification delivery | < 2 s (median and p90) | | [ ] | [ ] |
| Push failure rate | agree a bound before the pilot (suggestion, not from the MVP definition: FAILED < 5% of pushes) | | [ ] | [ ] |
| Physical pager usage | measurable reduction vs baseline | | [ ] | [ ] |

### 4.4 Rollback and kill switches

| Situation | Action |
|---|---|
| One restaurant misbehaves or must stop | Platform operator, **Restaurants**, **Deactivate**: new customer sessions are refused; existing sessions and orders stay. |
| Staff account compromised | Restaurant admin, **Staff**, **Deactivate** the account. Tokens live 1 h with a 7-day refresh grace; rotating `JWT_SECRET` signs everybody out. |
| Push misbehaving (wrong content, push service errors) | Configuration `readybell.vapid.enabled=false` makes `/api/push/config` report `enabled: false`, so the customer page hides **Notify me**; tracking and live updates keep working. Via env that is `READYBELL_VAPID_ENABLED=false` using Spring relaxed binding, but `docker-compose.yml` does not pass it through, so add it to the `app` environment first. Not tested. |
| Public endpoint abuse | Block at the proxy or lower the limits in `application.yml` (`readybell.rate-limit.rules`). `RATE_LIMIT_ENABLED=false` turns the limiter off; do not use it in production. |
| Bad release | Redeploy the previous image. Flyway migrations are forward-only (V1-V3 exist); never edit an applied one; restore from backup if a schema change must be undone. |
| Whole pilot stops | `docker compose stop app`; staff fall back to the pager/number process. Data stays in the `pgdata` volume. |
| Data loss | No backup job exists yet (BACKLOG P3). At minimum schedule `pg_dump` and test one restore before the pilot. |

### 4.5 Contacts

| Role | Name | Phone / chat | Hours |
|---|---|---|---|
| Pilot owner | | | |
| On-call engineer | | | |
| Hosting / DNS / TLS | | | |
| Restaurant 1 contact | | | |
| Restaurant 2 contact | | | |
| Restaurant 3 contact | | | |

## 5. Known limitations

- **Counter QR flow is not built (BACKLOG P4).** Today the QR is generated **per order** by staff in the **New order** dialog and printed on the receipt. A permanent per-restaurant QR where the customer types the order number from the receipt does not exist; `POST /api/sessions` starts a session from the restaurant identifier, but there is no endpoint yet to attach an existing order to it.
- **iOS needs the installed PWA.** Safari on iPhone (16.4+) delivers Web Push only to a site added to the Home Screen and opened from there. Customers must follow the hint on the page; expect some to skip it.
- **No custom push metrics or alerts (BACKLOG R2).** Watch logs and the `notification` / `outbox_event` tables during the pilot.
- **No backups (BACKLOG P3).**
- **Delivery time is not recorded by the system**; measure it by hand (3.1).
- **Sessions expire**: hourly housekeeping ends sessions older than 24 h with no open orders and deletes their push subscriptions.
- **Push TTL is 1 hour**: a phone that stays offline longer never gets the "ready" ping.
- **Staff tokens live in browser `localStorage`** (BACKLOG S3); use staff-only devices.
- The staff panel is English only (BACKLOG Q2); the customer page has English and Ukrainian.
- This document was written from the code and docs, not from a pilot run; nothing here has been verified on real devices or a real host.
