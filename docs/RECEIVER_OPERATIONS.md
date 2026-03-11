# Receiver Operations Guide

This guide covers operational guidance for webhook receiver verification and replay-store management.

## Replay-Store Backends

The starter ships with three implementations:

- `InMemoryPrivacyAuditDeadLetterWebhookReplayStore` for local development and tests
- `FilePrivacyAuditDeadLetterWebhookReplayStore` for single-instance deployments
- `JdbcPrivacyAuditDeadLetterWebhookReplayStore` for shared, multi-instance deployments

If you run multiple receiver instances, use a shared store implementation to avoid replay gaps.
Implement `PrivacyAuditDeadLetterWebhookReplayStore` and wire it as a bean to override the default.

### Choosing a Replay Store

- Use `InMemoryPrivacyAuditDeadLetterWebhookReplayStore` for local runs and tests only.
- Use `FilePrivacyAuditDeadLetterWebhookReplayStore` when you run a single receiver instance and can persist the file.
- Use `JdbcPrivacyAuditDeadLetterWebhookReplayStore` when you run multiple receiver instances or need shared state.

### Migrating from File to JDBC

- Plan for a clean cutover: existing nonces in the file store will not be automatically imported.
- During migration, rotate receiver secrets if you want to invalidate old nonces.
- Schedule a brief maintenance window and clear the replay store before switching.
- After migration, enable `replay-store.jdbc.initialize-schema` once, then disable it if you manage schema separately.

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
                jdbc:
                  enabled: true
                  initialize-schema: true
                  table-name: privacy_audit_webhook_replay_store
```

You can override the schema location or dialect via:

- `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc.schema-location`
- `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc.dialect`

### Schema Notes

Built-in schema resources:

- `classpath:META-INF/privacy-guard/privacy-audit-dead-letter-webhook-replay-store-schema-generic.sql`
- `classpath:META-INF/privacy-guard/privacy-audit-dead-letter-webhook-replay-store-schema-h2.sql`
- `classpath:META-INF/privacy-guard/privacy-audit-dead-letter-webhook-replay-store-schema-postgresql.sql`
- `classpath:META-INF/privacy-guard/privacy-audit-dead-letter-webhook-replay-store-schema-mysql.sql`

Use the dialect switch when your database vendor requires a custom schema variant.

## Health and Metrics

Receiver replay-store metrics are exposed when Micrometer is available:

- `privacy.audit.deadletters.receiver.replay_store.count`
- `privacy.audit.deadletters.receiver.replay_store.expiring_soon`
- `privacy.audit.deadletters.receiver.replay_store.expiry_seconds{kind=*}`

Use Actuator to query these metrics:

```
GET /actuator/metrics/privacy.audit.deadletters.receiver.replay_store.count
GET /actuator/metrics/privacy.audit.deadletters.receiver.replay_store.expiring_soon
GET /actuator/metrics/privacy.audit.deadletters.receiver.replay_store.expiry_seconds?tag=kind:earliest
```

## Dashboard Suggestions

Recommended charts:

- Replay store size: `privacy.audit.deadletters.receiver.replay_store.count`
- Expiring soon: `privacy.audit.deadletters.receiver.replay_store.expiring_soon`
- Earliest expiry: `privacy.audit.deadletters.receiver.replay_store.expiry_seconds{kind="earliest"}`

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

## Sample Operations

The sample app exposes protected replay-store endpoints:

- `GET /demo-alert-receiver/replay-store`
- `GET /demo-alert-receiver/replay-store/stats`
- `DELETE /demo-alert-receiver/replay-store`

Use these to inspect and clear the replay store during testing.
