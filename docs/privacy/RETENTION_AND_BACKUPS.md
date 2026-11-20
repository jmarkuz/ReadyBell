# Runbook: retention, backups and personal-data incidents

## How personal data leaves the system

| When | What happens |
|---|---|
| Every night (02:30) | the retention run: finished orders older than `order-days` lose name, items and link to the session; notification texts older than `notification-days` are emptied; ended sessions with no orders older than `session-days` are deleted. Each task is a row in `retention_run` |
| The customer presses "Delete my data" | push subscriptions deleted, name removed from every order of the link, finished orders anonymized at once; a row in `erasure_log` (keyed hash of the session id) |
| Hourly | housekeeping ends old sessions and deletes the subscriptions of ended sessions |

Check it works: `select task, started_at, affected, error from retention_run order by started_at desc limit 20;` and the metric
`readybell_retention_last_success_timestamp` (alert `RetentionNotRunning`). Try a configuration change first with
`READYBELL_RETENTION_DRY_RUN=true`: tasks only report how many rows they would change (`POST /api/platform/retention/run` shows the numbers).

## Restoring a backup

A backup still holds what the live database already forgot. After a restore, **before the service is opened to users**:

1. Start the application against the restored database with the same `JWT_SECRET` as before (the erasure log is keyed with it; with a
   different secret no entry can be matched).
2. As the platform operator call `POST /api/platform/erasures/replay` (for example `curl -u platform:... -X POST https://<host>/api/platform/erasures/replay`).
   It hashes every session of the restored database and repeats the erasure of those found in `erasure_log`. The answer says how many sessions had
   something to erase again.
3. Run the retention once without waiting for the night: `curl -u platform:... -X POST https://<host>/api/platform/retention/run` (the answer lists rows
   changed per task), so that old orders in the restored data are anonymized again.
4. Only then let customers and staff in.

Keep the secret: losing `JWT_SECRET` also loses the ability to match old log entries. Back it up the way you back up other secrets.

## Personal-data incident (breach)

1. **Contain**: revoke access (rotate `JWT_SECRET` signs everyone out; rotate the database password; block the leaked key or host).
2. **Assess**: which tables and which restaurants, from when to when (logs, `outbox_event`, access logs of the proxy). What kind of data: customer names and
   ordered items are low risk; staff password hashes and push device addresses matter more.
3. **Notify**: the affected restaurants (controllers) without undue delay (the agreement sets the limit, aim for 24 hours); the restaurant notifies the
   supervisory authority within 72 hours of becoming aware, if the risk requires it, and the customers if the risk is high.
4. **Record**: what happened, effects, actions (even if nothing is reported).
5. **Fix and learn**: root cause, regression test, review of the records of processing.
