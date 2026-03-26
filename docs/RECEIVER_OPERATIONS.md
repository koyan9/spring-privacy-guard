# Receiver Operations Guide

This guide covers operational guidance for webhook receiver verification and replay-store management.

## Replay-Store Backends

The starter ships with three implementations:

- `InMemoryPrivacyAuditDeadLetterWebhookReplayStore` for local development and tests
- `FilePrivacyAuditDeadLetterWebhookReplayStore` for single-instance deployments
- `RedisPrivacyAuditDeadLetterWebhookReplayStore` for shared nonce state with native TTL
- `JdbcPrivacyAuditDeadLetterWebhookReplayStore` for shared, multi-instance deployments

If you run multiple receiver instances, use a shared store implementation to avoid replay gaps.
Implement `PrivacyAuditDeadLetterWebhookReplayStore` and wire it as a bean to override the default.

### Replay Namespace Boundary

Receiver replay protection is deployment-scoped by default.
If multiple logical receiver domains share one replay-store backend, configure:

- `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.namespace`

The verifier prefixes stored nonces with that namespace before writing to the backend.
This keeps nonce spaces distinct across environments, per-tenant receiver deployments, or separate webhook consumers that intentionally share one JDBC / Redis / file replay store.

This namespace is static configuration, not per-request tenant resolution.
Do not rely on request headers to create replay boundaries unless your own receiver contract signs and validates that routing information separately.

### Tenant-Specific Receiver Routes

If one application hosts multiple tenant-specific receiver endpoints, configure explicit path routes instead of trying to select secrets from unverified request headers.

Example:

```yaml
privacy:
  guard:
    audit:
      dead-letter:
        observability:
          alert:
            receiver:
              filter:
                enabled: true
            tenant:
              enabled: true
              tenant-ids:
                - tenant-a
              routes:
                tenant-a:
                  receiver:
                    path-pattern: /tenant-a/privacy-alerts
                    bearer-token: tenant-a-token
                    signature-secret: tenant-a-secret
                    replay-namespace: tenant-a-receiver
```

Route behavior:

- the route `path-pattern` is matched before the global receiver path pattern
- route-specific bearer/signature settings override the global receiver verification settings for that path
- route-specific `replay-namespace` overrides the global replay namespace for that path
- if a route omits a verification field, the starter falls back to the global receiver verification settings

### Choosing a Replay Store

- Use `InMemoryPrivacyAuditDeadLetterWebhookReplayStore` for local runs and tests only.
- Use `FilePrivacyAuditDeadLetterWebhookReplayStore` when you run a single receiver instance and can persist the file.
- Use `RedisPrivacyAuditDeadLetterWebhookReplayStore` when you already operate Redis and want shared nonce state without JDBC schema management.
- Use `JdbcPrivacyAuditDeadLetterWebhookReplayStore` when you run multiple receiver instances or need shared state.

### Verification Settings

You can configure receiver verification settings directly with properties:

```yaml
privacy:
  guard:
    audit:
      dead-letter:
        observability:
          alert:
            receiver:
              verification:
                enabled: true
                bearer-token: demo-receiver-token
                signature-secret: demo-receiver-secret
                max-skew: 5m
```

If verification is enabled without a bearer token or signature secret, verification will not reject requests.

### Verification Failure Responses

Verification failures return a JSON response with a reason code:

```json
{"error":"Invalid signature","reason":"INVALID_SIGNATURE"}
```

Reason codes include `INVALID_AUTHORIZATION`, `MISSING_SIGNATURE_HEADERS`, `INVALID_TIMESTAMP`, `EXPIRED_TIMESTAMP`, `INVALID_SIGNATURE`, and `REPLAY_DETECTED`.

### Migrating from File to JDBC

- Existing nonces in the file store are not imported automatically.
- Rotate receiver secrets during cutover if you want to invalidate previously accepted nonces.
- Follow `docs/JDBC_PRODUCTION_GUIDE.md` for the recommended cutover order, schema-management strategy, and post-migration validation.

### File Replay Store

Enable the file replay store for single-instance deployments:

```yaml
privacy:
  guard:
    audit:
      dead-letter:
        observability:
          alert:
            receiver:
              replay-store:
                namespace: tenant-a-receiver
                file:
                  enabled: true
                  path: /var/lib/privacy-audit/replay-store.json
```

### JDBC Replay Store

Enable the JDBC replay store when running multiple receiver instances:

```yaml
privacy:
  guard:
    audit:
      dead-letter:
        observability:
          alert:
            receiver:
              replay-store:
                namespace: tenant-a-receiver
                jdbc:
                  enabled: true
                  initialize-schema: true
                  table-name: privacy_audit_webhook_replay_store
                  cleanup-interval: 5m
```

You can override the schema location or dialect via:

- `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc.schema-location`
- `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc.dialect`

`cleanup-interval` controls how often the global cleanup runs. Set it to `0` to clean on every request.
For broader MySQL / PostgreSQL production rollout guidance, index recommendations, and migration notes, see `docs/JDBC_PRODUCTION_GUIDE.md`.

### Redis Replay Store

Enable the Redis replay store when you want shared nonce protection with native key expiry:

```yaml
privacy:
  guard:
    audit:
      dead-letter:
        observability:
          alert:
            receiver:
              replay-store:
                namespace: tenant-a-receiver
                redis:
                  enabled: true
                  key-prefix: privacy:audit:webhook:replay:
                  scan-batch-size: 500
```

Redis mode stores one key per nonce and relies on Redis TTL for expiry. Snapshot and clear operations iterate keys by prefix, so use a dedicated prefix for this library.

### Sample Redis Multi-Instance Reference

The sample app now includes:

- `samples/privacy-demo/src/main/resources/application-redis-tenant.yml`
- `samples/privacy-demo/src/main/resources/application-redis-tenant-node2.yml`
- `samples/privacy-demo/docker-compose.redis.yml`
- `samples/privacy-demo/scripts/manage-redis-local.ps1`
- `samples/privacy-demo/scripts/manage-redis-local.sh`
- `samples/privacy-demo/scripts/verify-redis-tenant-multi-instance.ps1`

Use these when you want a local two-node receiver verification rehearsal backed by Redis instead of JDBC replay-store tables.

Recommended local flow:

1. Start Redis on `localhost:6379`.
   Use `manage-redis-local.ps1 -Action up` on Windows or `manage-redis-local.sh up` on macOS / Linux if you want the sample to launch Redis through the bundled Docker Compose file.
2. Start node 1 with profile `redis-tenant`.
3. Start node 2 with profiles `redis-tenant,redis-tenant-node2`.
4. Run `verify-redis-tenant-multi-instance.ps1`.
5. Confirm the second node rejects the replayed nonce and both nodes report `receiverReplayStore.backend=REDIS` in `/demo-tenants/observability`.

### Schema Notes

Built-in schema resources:

- `classpath:META-INF/privacy-guard/privacy-audit-dead-letter-webhook-replay-store-schema-generic.sql`
- `classpath:META-INF/privacy-guard/privacy-audit-dead-letter-webhook-replay-store-schema-h2.sql`
- `classpath:META-INF/privacy-guard/privacy-audit-dead-letter-webhook-replay-store-schema-postgresql.sql`
- `classpath:META-INF/privacy-guard/privacy-audit-dead-letter-webhook-replay-store-schema-mysql.sql`

Use the dialect switch when your database vendor requires a custom schema variant.

## Health and Metrics

Receiver replay-store metrics are exposed when Micrometer is available:

Webhook alert delivery diagnostics metrics (when enabled):

- `privacy.audit.deadletters.alert.webhook.failures{type=*,retryable=*,category=*}`
- `privacy.audit.deadletters.alert.webhook.last_failure_status`
- `privacy.audit.deadletters.alert.webhook.last_failure_retryable`
- `privacy.audit.deadletters.alert.webhook.last_failure_type{type=*}`
- `privacy.audit.deadletters.receiver.verification.failures{reason=*}`

- `privacy.audit.deadletters.receiver.replay_store.count`
- `privacy.audit.deadletters.receiver.replay_store.expiring_soon`
- `privacy.audit.deadletters.receiver.replay_store.expiry_seconds{kind=*}`
- `privacy.audit.deadletters.receiver.replay_store.cleanup.last_count`
- `privacy.audit.deadletters.receiver.replay_store.cleanup.last_duration_ms`
- `privacy.audit.deadletters.receiver.replay_store.cleanup.last_timestamp`

Snapshot and metric counts exclude expired entries even if cleanup has not run yet.

Use Actuator to query these metrics:

```
GET /actuator/metrics/privacy.audit.deadletters.receiver.replay_store.count
GET /actuator/metrics/privacy.audit.deadletters.receiver.replay_store.expiring_soon
GET /actuator/metrics/privacy.audit.deadletters.receiver.replay_store.expiry_seconds?tag=kind:earliest
GET /actuator/metrics/privacy.audit.deadletters.receiver.replay_store.cleanup.last_count
GET /actuator/metrics/privacy.audit.deadletters.receiver.replay_store.cleanup.last_duration_ms
GET /actuator/metrics/privacy.audit.deadletters.receiver.replay_store.cleanup.last_timestamp
```

## Dashboard Suggestions

Recommended charts:

- Replay store size: `privacy.audit.deadletters.receiver.replay_store.count`
- Expiring soon: `privacy.audit.deadletters.receiver.replay_store.expiring_soon`
- Earliest expiry: `privacy.audit.deadletters.receiver.replay_store.expiry_seconds{kind="earliest"}`
- Last cleanup count: `privacy.audit.deadletters.receiver.replay_store.cleanup.last_count`

Alerting ideas:

- Trigger a warning if `expiring_soon` spikes beyond normal baseline.
- Investigate if `count` grows while webhook delivery success remains stable.

## Deployment Patterns

Single instance:

- Use the file-backed replay store and persist the file on durable storage.
- Protect receiver management endpoints with an admin token.

Multiple instances:

- Prefer a shared replay store to prevent nonce reuse across instances.
- If you cannot share a store, use sticky sessions and document the limitation.
- If multiple tenant-specific receiver deployments share the same store, give each deployment its own `replay-store.namespace`.
- For a runnable local two-node example, reuse `samples/privacy-demo` with the `redis-tenant` and `redis-tenant-node2` profiles when you already have Redis available.

## Sample Operations

The sample app exposes protected replay-store endpoints:

- `GET /demo-alert-receiver/replay-store`
- `GET /demo-alert-receiver/replay-store/stats`
- `DELETE /demo-alert-receiver/replay-store`

Use these to inspect and clear the replay store during testing.
