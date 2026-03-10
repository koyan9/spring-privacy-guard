# Receiver Operations Guide

This guide covers operational guidance for webhook receiver verification and replay-store management.

## Replay-Store Backends

The starter ships with two implementations:

- `InMemoryPrivacyAuditDeadLetterWebhookReplayStore` for local development and tests
- `FilePrivacyAuditDeadLetterWebhookReplayStore` for single-instance deployments

If you run multiple receiver instances, use a shared store implementation to avoid replay gaps.
Implement `PrivacyAuditDeadLetterWebhookReplayStore` and wire it as a bean to override the default.

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
