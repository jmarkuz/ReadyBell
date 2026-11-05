# Monitoring

ReadyBell exposes Micrometer metrics at `/actuator/prometheus` (readable only by the platform operator; use HTTP Basic).
Alert rules live in `ops/prometheus/alerts.yml`, a sample scrape config in `ops/prometheus/prometheus.yml`.

Tags are always low-cardinality enums. Tenant ids and session ids are never used as tags.

## Business metrics

| Prometheus name | Type | Meaning |
|-----------------|------|---------|
| `readybell_sessions_started_total` | counter | Customer sessions started (QR scanned) |
| `readybell_orders_created_total` | counter | Orders created |
| `readybell_orders_transitions_total{status}` | counter | Status changes by the status entered (`CREATED`, `IN_PROGRESS`, `READY`, `COMPLETED`, `CANCELLED`) |
| `readybell_push_attempts_total{result}` | counter | Web Push send attempts: `sent` (accepted by the push service), `failed` (rejected, transient error, exception), `expired` (404/410, subscription removed). Each retry is an attempt |
| `readybell_push_ready_latency_seconds` | histogram | Time from the order becoming READY to the push service accepting a push (buckets 0.25, 0.5, 1, 2, 5, 10, 30 s) |
| `readybell_sse_connections{audience}` | gauge | Open SSE connections on this node, `audience` = `staff` or `customer` (sum over instances for the total) |

Events behind the counters travel through the outbox, which delivers at least once; after a relay crash an event can be
counted twice, so treat the counters as accurate to a fraction of a percent, not as accounting.

## Platform metrics already present

| Name | Meaning |
|------|---------|
| `outbox_pending`, `outbox_failed` (gauges) | Outbox events waiting / given up after the retry limit |
| `ratelimit_rejected_total{endpoint}` | Requests answered 429 |
| `http_server_requests_seconds_*` | Request count and latency by status and URI (Spring Boot) |
| `jvm_*`, `hikaricp_*`, `process_*` | JVM, connection pool, process |

## MVP success numbers

- **Customer sessions (target 500+)**: `sum(increase(readybell_sessions_started_total[30d]))`, or just
  `sum(readybell_sessions_started_total)` since the instances started (counters reset on restart; `increase` handles that).
- **Push delivery under 2 s after READY**:
  - p95: `histogram_quantile(0.95, sum by (le) (rate(readybell_push_ready_latency_seconds_bucket[1h])))`
  - share under 2 s: `sum(rate(readybell_push_ready_latency_seconds_bucket{le="2.0"}[1h])) / sum(rate(readybell_push_ready_latency_seconds_count[1h]))`
  - This measures READY until the push service accepted the message. Delivery to the device after that is up to the push
    service and is not observable server-side.
- **Push success ratio**: `sum(rate(readybell_push_attempts_total{result="sent"}[1h])) / sum(rate(readybell_push_attempts_total[1h]))`
- **Orders per status**: `sum by (status) (increase(readybell_orders_transitions_total[1d]))`

## Alerts

| Alert | Fires when |
|-------|-----------|
| `ReadyBellDown` | scrape target down 2 min |
| `HighServerErrorRate` | 5xx above 5% of requests for 5 min |
| `PushFailureRatioHigh` | more than 20% of push attempts failed or expired for 10 min |
| `PushNotSendingAtAll` | orders turned READY, pushes were attempted, none accepted (check VAPID keys) |
| `ReadyToPushLatencySlow` | p95 READY to push above 2 s for 10 min |
| `OutboxFailedEvents` | `outbox_failed > 0` (events gave up; investigate and requeue) |
| `OutboxBacklogGrowing` | pending outbox events never drain and are growing |
| `SseConnectionsDroppedToZero` | optional: no SSE connection although orders are created |

Validate the rules with `docker run --rm --entrypoint promtool -v "$PWD/ops/prometheus:/p:ro" prom/prometheus check rules /p/alerts.yml`.

## Running Prometheus next to docker compose

1. Put the platform operator's password in `ops/prometheus/readybell-scrape-password` (plain text, no newline) and set
   the `username` in `ops/prometheus/prometheus.yml` to your `PLATFORM_ADMIN_USERNAME`. Do not commit the password file.
   The operator password is the plain one; `PLATFORM_ADMIN_PASSWORD_HASH` in `.env` is only its bcrypt hash.
2. Start Prometheus on the compose network (replace `<project>` with the compose project name, see `docker network ls`):

   ```bash
   docker run -d --name prometheus --network <project>_default -p 9090:9090 \
     -v "$PWD/ops/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro" \
     -v "$PWD/ops/prometheus/alerts.yml:/etc/prometheus/alerts.yml:ro" \
     -v "$PWD/ops/prometheus/readybell-scrape-password:/etc/prometheus/readybell-scrape-password:ro" \
     prom/prometheus
   ```

3. Open http://localhost:9090/targets (target `readybell` should be UP) and http://localhost:9090/alerts.
   Routing alerts to e-mail/Slack needs Alertmanager, which is not part of this setup.
