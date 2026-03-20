# Tenant Observability Guide

This guide explains how to use the tenant-aware Micrometer metrics added by `spring-privacy-guard` to understand which multi-tenant paths are active in production.

## Metrics

When Micrometer is present, the starter can emit:

- `privacy.audit.tenant.read.path{domain=*,path=*}`
- `privacy.audit.tenant.write.path{domain=*,path=*}`

These counters are about execution path selection, not business volume by tenant.

## Read Path Dimensions

`privacy.audit.tenant.read.path` uses these domains:

- `audit`
- `audit_stats`
- `dead_letter`
- `dead_letter_stats`

`path` currently means:

- `native`
  The helper used a tenant-native repository SPI implementation.
- `fallback`
  The helper fell back to cross-page filtering on the existing repository results.

## Write Path Dimensions

`privacy.audit.tenant.write.path` uses these domains:

- `audit_write`
  Direct repository-backed audit writes.
- `audit_batch_write`
  Buffered or batched repository writes.
- `dead_letter_write`
  Repository-backed dead-letter persistence.

`path` currently means:

- `native`
  The write path used tenant-aware write SPI hints.
- `fallback`
  The write path used the legacy repository contract without tenant-aware hints.

## What Good Looks Like

Typical expectations:

- If you enabled tenant-native read SPI in built-in JDBC or custom repositories, `read.path{path="native"}` should dominate over time.
- If you enabled tenant-aware write SPI via built-in in-memory or JDBC repositories, `write.path{path="native"}` should dominate for the corresponding write domain.
- During rollout, a temporary mix of `native` and `fallback` is acceptable if not every path has been migrated yet.

## What to Investigate

Investigate these patterns:

- `read.path{path="fallback"}` keeps growing after you expected repository-native read support to be active.
  Check whether the repository bean actually implements `PrivacyTenantAuditReadRepository` or `PrivacyTenantAuditDeadLetterReadRepository`.
- `write.path{path="fallback"}` keeps growing after you expected tenant-aware persistence hints.
  Check whether the repository bean actually implements `PrivacyTenantAuditWriteRepository` or `PrivacyTenantAuditDeadLetterWriteRepository`.
- JDBC tenant columns are enabled but read path is still mostly `fallback`.
  Check whether the running repository bean is still a non-JDBC or custom implementation.
- Batch write path is `fallback` while direct write path is `native`.
  Check whether the buffered publisher is wrapping a repository that exposes tenant-aware batch semantics.

## Actuator Queries

Examples:

```text
GET /actuator/metrics/privacy.audit.tenant.read.path
GET /actuator/metrics/privacy.audit.tenant.read.path?tag=domain:audit&tag=path:native
GET /actuator/metrics/privacy.audit.tenant.read.path?tag=domain:dead_letter&tag=path:fallback
GET /actuator/metrics/privacy.audit.tenant.write.path
GET /actuator/metrics/privacy.audit.tenant.write.path?tag=domain:audit_write&tag=path:native
GET /actuator/metrics/privacy.audit.tenant.write.path?tag=domain:audit_batch_write&tag=path:fallback
```

## Rollout Checklist

Use these metrics during rollout:

1. Enable tenant policies first.
2. Verify functional correctness.
3. Enable JDBC tenant columns or custom tenant-aware repository SPI.
4. Watch `privacy.audit.tenant.read.path`.
5. Watch `privacy.audit.tenant.write.path`.
6. Only call the migration complete when the expected domains have moved to `native`.

## Sample Profile

The local JDBC tenant sample profile in:

- `samples/privacy-demo/src/main/resources/application-jdbc-tenant.yml`

is a good place to validate the expected `native` path behavior before changing a production deployment.

## Related Docs

- `docs/MULTI_TENANT_GUIDE.md`
- `docs/TENANT_ADOPTION_PLAYBOOK.md`
- `docs/JDBC_PRODUCTION_GUIDE.md`
- `samples/privacy-demo/README.md`
