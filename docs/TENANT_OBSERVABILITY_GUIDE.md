# Tenant Observability Guide

This guide explains how to use the tenant-aware Micrometer metrics added by `spring-privacy-guard` to understand which multi-tenant paths are active in production.

## Metrics

When Micrometer is present, the starter can emit:

- `privacy.audit.tenant.read.path{domain=*,path=*}`
- `privacy.audit.tenant.write.path{domain=*,path=*}`
- `privacy.audit.deadletters.alert.tenant.transitions{tenant=*,state=*,recovery=*}`
- `privacy.audit.deadletters.alert.tenant.deliveries{tenant=*,channel=*,outcome=*}`
- `privacy.audit.deadletters.receiver.route.failures{route=*,reason=*}`

These counters are about execution path selection, not business volume by tenant.
The path counters should stay low-cardinality and do not add a `tenant` tag.

For tenant-scoped backlog state, combine these counters with `PrivacyTenantAuditDeadLetterObservationService` or the sample `/demo-tenants/observability` endpoint.
Those views reuse the effective tenant or global warning/down thresholds to summarize dead-letter backlog state per tenant without changing the stable dead-letter entry contract.
If you also enable `privacy.guard.audit.dead-letter.observability.alert.tenant.enabled=true`, the same tenant list can drive callback fan-out through `PrivacyTenantAuditDeadLetterAlertMonitor`.
Preferred tenant-specific webhook, email, and receiver route overrides now live under `privacy.guard.tenant.policies.<tenantId>.observability.dead-letter.alert.*`.
The legacy `privacy.guard.audit.dead-letter.observability.alert.tenant.routes.<tenantId>.*` path remains supported and bridges into the same effective tenant route policy.
Tenant policy can also independently disable logging, webhook, or email delivery per tenant through `privacy.guard.tenant.policies.<tenantId>.observability.dead-letter.alert.{logging,webhook,email}.enabled`.

## Tenant Health Endpoint

If you need tenant backlog state directly in Actuator health, enable:

- `privacy.guard.audit.dead-letter.observability.health.tenant-enabled=true`

Optional:

- `privacy.guard.audit.dead-letter.observability.health.tenant-ids=*`

When `tenant-ids` is empty, the starter falls back to the configured metric tenant IDs, the default tenant, and tenant policy keys.

This adds a tenant summary under:

- `GET /actuator/health`

The endpoint reports:

- overall `UP` when every configured tenant is `CLEAR` or `WARNING`
- overall `DOWN` when any configured tenant backlog reaches the down threshold
- per-tenant details including `state`, `total`, `warningThreshold`, and `downThreshold`
- per-tenant threshold details reflect tenant policy overrides when `privacy.guard.tenant.policies.<tenantId>.observability.dead-letter.*` is configured

## Read Path Dimensions

`privacy.audit.tenant.read.path` uses these domains:

- `audit`
- `audit_stats`
- `dead_letter`
- `dead_letter_stats`
- `dead_letter_find_by_id`
- `dead_letter_export`
- `dead_letter_manifest`

`path` currently means:

- `native`
  The helper used a tenant-native repository SPI implementation.
- `fallback`
  The helper fell back to cross-page filtering on the existing repository results.

For the exchange domains:

- `dead_letter_find_by_id`
  `native` means tenant-scoped single-entry lookup by `id` reused a tenant-native dead-letter read repository implementation.
  `fallback` means the helper first used the generic repository `findById(id)` path and then enforced tenant ownership in the helper layer.

- `dead_letter_export`
  `native` means tenant-scoped export reused a tenant-native dead-letter read repository.
  `fallback` means export reused helper-driven filtering over the generic repository path.
- `dead_letter_manifest`
  `native` means tenant-scoped manifest generation reused a tenant-native dead-letter read repository.
  `fallback` means manifest generation reused helper-driven filtering over the generic repository path.

## Write Path Dimensions

`privacy.audit.tenant.write.path` uses these domains:

- `audit_write`
  Direct repository-backed audit writes.
- `audit_batch_write`
  Buffered or batched repository writes.
- `dead_letter_write`
  Repository-backed dead-letter persistence.
- `dead_letter_import`
  Tenant-scoped dead-letter import persistence.
- `dead_letter_delete`
  Tenant-scoped dead-letter criteria deletes.
- `dead_letter_delete_by_id`
  Tenant-scoped single-entry dead-letter deletes.
- `dead_letter_replay`
  Tenant-scoped dead-letter criteria replay orchestration.
- `dead_letter_replay_by_id`
  Tenant-scoped single-entry dead-letter replay orchestration.

`path` currently means:

- `native`
  The write path used tenant-aware write SPI hints.
- `fallback`
  The write path used the legacy repository contract without tenant-aware hints.

For the newer operation domains:

- `dead_letter_delete`
  `native` means the operation used `PrivacyTenantAuditDeadLetterDeleteRepository`.
  `fallback` means the helper first selected tenant-scoped rows and then deleted them one by one.
- `dead_letter_delete_by_id`
  `native` means the operation used `PrivacyTenantAuditDeadLetterDeleteRepository.deleteById(...)`.
  `fallback` means the helper first looked up the tenant-scoped entry by `id` and then reused the global delete-by-id path.
- `dead_letter_import`
  `native` means the import path used `PrivacyTenantAuditDeadLetterWriteRepository.saveAllTenantAware(...)`.
  `fallback` means the tenant exchange helper retagged the entries and then reused the generic dead-letter import path.
- `dead_letter_replay`
  `native` means the operation used `PrivacyTenantAuditDeadLetterReplayRepository`.
  `fallback` means the helper first selected tenant-scoped rows and replayed them through the legacy service path because the running repository does not implement the replay SPI.
- `dead_letter_replay_by_id`
  `native` means the operation used `PrivacyTenantAuditDeadLetterReplayRepository.replayById(...)`.
  `fallback` means the helper first looked up the tenant-scoped entry by `id` and then replayed it through the legacy service path.

## What Good Looks Like

Typical expectations:

- Built-in in-memory and JDBC repositories both implement the tenant-aware read/write SPI, so those profiles should normally emit `path="native"` for the supported domains.
- If you enabled tenant-native read SPI in built-in JDBC or custom repositories, `read.path{path="native"}` should dominate over time.
- If you enabled tenant-aware write SPI via built-in in-memory or JDBC repositories, `write.path{path="native"}` should dominate for the corresponding write domain.
- Built-in native write paths also materialize the configured tenant detail key during persistence when the write request carries a tenant ID, so tenant-native reads can still work even if service-level audit policy uses `attachTenantId=false`.
- Built-in in-memory and JDBC dead-letter repositories also implement tenant-native delete SPI, so `write.path{domain="dead_letter_delete",path="native"}` should dominate for tenant criteria deletes.
- Built-in in-memory and JDBC dead-letter repositories also implement tenant-native delete-by-id SPI, so `write.path{domain="dead_letter_delete_by_id",path="native"}` should dominate for tenant single-entry deletes in those profiles.
- Built-in in-memory and JDBC dead-letter repositories also implement tenant-native replay SPI, so `write.path{domain="dead_letter_replay",path="native"}` should dominate for tenant criteria replay in those profiles.
- Built-in in-memory and JDBC dead-letter repositories also implement tenant-native replay-by-id SPI, so `write.path{domain="dead_letter_replay_by_id",path="native"}` should dominate for tenant single-entry replay in those profiles.
- Built-in in-memory and JDBC dead-letter repositories also implement tenant-native single-entry lookup by `id`, so `read.path{domain="dead_letter_find_by_id",path="native"}` should dominate for tenant-scoped single-entry management flows in those profiles.
- Tenant alert transitions should accumulate under `privacy.audit.deadletters.alert.tenant.transitions` when backlog state moves between `CLEAR`, `WARNING`, and `DOWN`.
- Built-in tenant logging/webhook/email callbacks should accumulate delivery outcome counters under `privacy.audit.deadletters.alert.tenant.deliveries`.
- Receiver route verification failures should show the matched path pattern under `privacy.audit.deadletters.receiver.route.failures`.
- During rollout, a temporary mix of `native` and `fallback` is acceptable if not every path has been migrated yet.

## What to Investigate

Investigate these patterns:

- `read.path{path="fallback"}` keeps growing after you expected repository-native read support to be active.
  Check whether the repository bean actually implements `PrivacyTenantAuditReadRepository` or `PrivacyTenantAuditDeadLetterReadRepository`.
- `read.path{domain="dead_letter_find_by_id",path="fallback"}` keeps growing after you expected native tenant ownership lookup for single-entry management.
  Check whether the running dead-letter repository actually overrides the tenant read SPI lookup by `id`, or whether the deployment is still on a generic-only repository implementation.
- `write.path{path="fallback"}` keeps growing after you expected tenant-aware persistence hints.
  Check whether the repository bean actually implements `PrivacyTenantAuditWriteRepository` or `PrivacyTenantAuditDeadLetterWriteRepository`.
- `write.path{domain="dead_letter_delete",path="fallback"}` keeps growing after you expected repository-native tenant delete support.
  Check whether the running repository bean actually implements `PrivacyTenantAuditDeadLetterDeleteRepository`.
- JDBC tenant columns are enabled but read path is still mostly `fallback`.
  Check whether the running repository bean is still a non-JDBC or custom implementation.
- Batch write path is `fallback` while direct write path is `native`.
  Check whether the buffered publisher is wrapping a repository that exposes tenant-aware batch semantics.
- `write.path{domain="dead_letter_replay",path="fallback"}` grows steadily.
  Check whether the running repository bean actually implements `PrivacyTenantAuditDeadLetterReplayRepository`.
- `privacy.audit.deadletters.alert.tenant.deliveries{outcome="failure"}` grows for one tenant.
  Check the tenant-specific webhook/email route override under the tenant policy path (or the legacy route path), remote target health, and the sample or production callback logs for the same tenant.
- `privacy.audit.deadletters.receiver.route.failures` grows on one path.
  Check whether the matched route is using the expected bearer token, signature secret, and replay-store namespace.
- The sample endpoint shows `native` for both the default and `jdbc-tenant` profiles.
  That is expected because the built-in in-memory and JDBC repositories already implement the tenant-aware read/write/delete/replay SPI. Persistent `fallback` usually indicates a custom repository or an incomplete migration.

## Actuator Queries

Examples:

```text
GET /actuator/health
GET /actuator/metrics/privacy.audit.tenant.read.path
GET /actuator/metrics/privacy.audit.tenant.read.path?tag=domain:audit&tag=path:native
GET /actuator/metrics/privacy.audit.tenant.read.path?tag=domain:dead_letter&tag=path:fallback
GET /actuator/metrics/privacy.audit.tenant.read.path?tag=domain:dead_letter_find_by_id&tag=path:native
GET /actuator/metrics/privacy.audit.tenant.read.path?tag=domain:dead_letter_export&tag=path:native
GET /actuator/metrics/privacy.audit.tenant.read.path?tag=domain:dead_letter_manifest&tag=path:native
GET /actuator/metrics/privacy.audit.tenant.write.path
GET /actuator/metrics/privacy.audit.tenant.write.path?tag=domain:audit_write&tag=path:native
GET /actuator/metrics/privacy.audit.tenant.write.path?tag=domain:audit_batch_write&tag=path:fallback
GET /actuator/metrics/privacy.audit.tenant.write.path?tag=domain:dead_letter_import&tag=path:native
GET /actuator/metrics/privacy.audit.tenant.write.path?tag=domain:dead_letter_delete&tag=path:native
GET /actuator/metrics/privacy.audit.tenant.write.path?tag=domain:dead_letter_replay&tag=path:native
GET /actuator/metrics/privacy.audit.deadletters.alert.tenant.transitions?tag=tenant:tenant-a&tag=state:warning&tag=recovery:false
GET /actuator/metrics/privacy.audit.deadletters.alert.tenant.deliveries?tag=tenant:tenant-a&tag=channel:logging&tag=outcome:success
GET /actuator/metrics/privacy.audit.deadletters.receiver.route.failures?tag=route:/demo-alert-receiver&tag=reason:invalid_signature
```

## Rollout Checklist

Use these metrics during rollout:

1. Enable tenant policies first.
2. Verify functional correctness.
3. Enable JDBC tenant columns or custom tenant-aware repository SPI.
4. Watch `privacy.audit.tenant.read.path`.
5. Watch `privacy.audit.tenant.write.path`.
6. Watch tenant alert transition and delivery metrics for the tenants you route separately.
7. Watch receiver route failures for tenant-specific callback paths.
8. Only call the migration complete when the expected domains have moved to `native`.

## Sample Profile

The local JDBC tenant sample profile in:

- `samples/privacy-demo/src/main/resources/application-jdbc-tenant.yml`

is a good place to validate the expected `native` path behavior before changing a production deployment.

If you want a lightweight two-node rehearsal without adding Redis or another external dependency, start a second sample process with:

- `samples/privacy-demo/src/main/resources/application-jdbc-tenant-node2.yml`

The two profiles share the same file-backed H2 JDBC database and JDBC replay-store so you can check whether both nodes report the same backlog and replay-store state.

If you already run Redis and want to validate shared replay-store behavior without JDBC replay-store schema management, use:

- `samples/privacy-demo/src/main/resources/application-redis-tenant.yml`
- `samples/privacy-demo/src/main/resources/application-redis-tenant-node2.yml`
- `samples/privacy-demo/docker-compose.redis.yml`
- `samples/privacy-demo/scripts/manage-redis-local.ps1`
- `samples/privacy-demo/scripts/manage-redis-local.sh`

Those profiles keep the same tenant observability surface but switch the receiver replay-store backend to Redis.

If you want a more production-like local reference with JDBC audit/dead-letter persistence plus shared Redis replay-store protection, use:

- `samples/privacy-demo/src/main/resources/application-postgres-redis-tenant.yml`
- `samples/privacy-demo/src/main/resources/application-postgres-redis-tenant-node2.yml`
- `samples/privacy-demo/docker-compose.postgres-redis.yml`
- `samples/privacy-demo/scripts/manage-postgres-redis-local.ps1`
- `samples/privacy-demo/scripts/manage-postgres-redis-local.sh`
- `samples/privacy-demo/scripts/verify-postgres-redis-tenant-multi-instance.ps1`
- `samples/privacy-demo/scripts/verify-postgres-redis-tenant-multi-instance.sh`

If you want a local contrast profile that intentionally exercises helper-driven tenant fallback paths, use:

- `samples/privacy-demo/src/main/resources/application-fallback-tenant.yml`

That profile disables the built-in tenant-aware repository implementations and replaces them with generic-only sample repositories so `/demo-tenants/observability` reports `fallback` for the relevant query/write/exchange paths.

If you want a local custom SPI reference that still reports `native`, use:

- `samples/privacy-demo/src/main/resources/application-custom-tenant-native.yml`

That profile disables the built-in repositories but replaces them with sample-defined tenant-aware repositories that implement the tenant read/write/delete/replay SPI directly.

If you want a closer production-style custom repository reference while still staying inside the sample, use:

- `samples/privacy-demo/src/main/resources/application-custom-jdbc-tenant.yml`
- `samples/privacy-demo/src/main/resources/application-custom-jdbc-tenant-node2.yml`

That profile keeps JDBC storage and schema initialization enabled, but swaps in sample-defined JDBC repository beans so `/demo-tenants/observability` shows a custom implementation name with `native` tenant capability flags.
For the local two-node rehearsal path, use:

- `samples/privacy-demo/scripts/verify-custom-jdbc-tenant-multi-instance.ps1`
- `samples/privacy-demo/scripts/verify-custom-jdbc-tenant-multi-instance.sh`

The sample also exposes a protected JSON snapshot endpoint:

- `GET /demo-tenants/observability`

Use it with:

- `X-Demo-Admin-Token: demo-admin-token`
- optionally `X-Privacy-Tenant: tenant-a`

The response summarizes the current read/write path counters, the configured repository types, and the relevant Actuator queries including the tenant health endpoint, which makes it useful for quick local verification before switching to raw Actuator queries.
It also includes an `expectedPaths` view derived from the declared tenant-native SPI capabilities, which makes it easier to compare “what this deployment should prefer” against the observed native/fallback counters.

If you want to validate shared receiver replay protection with Redis instead of JDBC replay-store tables, use:

- `samples/privacy-demo/src/main/resources/application-redis-tenant.yml`
- `samples/privacy-demo/src/main/resources/application-redis-tenant-node2.yml`
- `samples/privacy-demo/scripts/manage-redis-local.ps1`

In that mode, `/demo-tenants/observability` also reports `receiverReplayStore.backend=REDIS`, which makes it easy to confirm that the sample is exercising the shared Redis nonce path rather than the file or JDBC store.

## Related Docs

- `docs/MULTI_TENANT_GUIDE.md`
- `docs/TENANT_ADOPTION_PLAYBOOK.md`
- `docs/JDBC_PRODUCTION_GUIDE.md`
- `samples/privacy-demo/README.md`
