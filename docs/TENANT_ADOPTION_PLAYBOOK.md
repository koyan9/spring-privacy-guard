# Tenant Adoption Playbook

This playbook helps teams choose and roll out the right multi-tenant persistence model for `spring-privacy-guard`.

Use it together with:

- `docs/MULTI_TENANT_GUIDE.md`
- `docs/JDBC_PRODUCTION_GUIDE.md`
- `docs/TENANT_OBSERVABILITY_GUIDE.md`

## Decision Matrix

Choose the smallest model that matches your operational needs.

| Model | Recommended When | Tradeoff |
| --- | --- | --- |
| Detail-key only | Low volume, in-memory demos, or early rollout where tenant filtering happens mostly in helper services | Easiest to adopt, but tenant reads may fall back to cross-page filtering |
| JDBC tenant column | Production JDBC deployments that need faster tenant-scoped query and stats paths without changing the stable event/query contracts | Requires schema migration and a consistent tenant detail key |
| Custom tenant-aware read/write SPI | Strict isolation, custom table layouts, external storage rules, or database-native optimization beyond the built-in repository model | Highest control, but you own repository implementation and migration discipline |

## Recommended Defaults

Use these defaults unless you have a clear reason not to:

1. Start with tenant detail tagging in `PrivacyAuditService` via tenant policy config.
2. Add `tenant-column-name` for JDBC-backed audit and dead-letter tables before tenant-heavy production traffic.
3. Keep the tenant detail key stable across write, query, replay, export, and import flows.
4. Override tenant dead-letter thresholds and recovery notifications with `privacy.guard.tenant.policies.<tenantId>.observability.dead-letter.*` only when different tenants genuinely need different operational behavior.
5. Only introduce custom tenant-aware repository SPI when the built-in JDBC model is measurably insufficient.

## Model 1: Detail-Key Only

Configuration shape:

```yaml
privacy:
  guard:
    tenant:
      enabled: true
      default-tenant: public
      policies:
        tenant-a:
          audit:
            attach-tenant-id: true
            tenant-detail-key: tenant
```

What you get:

- tenant-aware masking and text rules
- tenant tags written into audit and dead-letter detail maps
- tenant-aware helper services and management facade

What you do not get:

- repository-native SQL filtering
- dedicated tenant indexes

Use this model when you are validating functional behavior first and performance is not yet the main concern.

## Model 2: JDBC Tenant Column

Configuration shape:

```yaml
privacy:
  guard:
    audit:
      repository-type: JDBC
      jdbc:
        table-name: privacy_audit_event
        tenant-column-name: tenant_id
        tenant-detail-key: tenant
      dead-letter:
        repository-type: JDBC
        jdbc:
          table-name: privacy_audit_dead_letter
          tenant-column-name: tenant_id
          tenant-detail-key: tenant
```

What you get:

- built-in JDBC repositories copy the configured detail key into the dedicated tenant column on write
- built-in tenant read helpers prefer column-based filtering instead of `details_json like`
- straightforward tenant indexes for common read paths

Recommended DDL shape:

PostgreSQL:

```sql
alter table privacy_audit_event add column if not exists tenant_id varchar(255);
create index if not exists idx_privacy_audit_event_tenant_id on privacy_audit_event (tenant_id);

alter table privacy_audit_dead_letter add column if not exists tenant_id varchar(255);
create index if not exists idx_privacy_audit_dead_letter_tenant_id on privacy_audit_dead_letter (tenant_id);
```

MySQL:

```sql
alter table privacy_audit_event add column tenant_id varchar(255) null;
create index idx_privacy_audit_event_tenant_id on privacy_audit_event (tenant_id);

alter table privacy_audit_dead_letter add column tenant_id varchar(255) null;
create index idx_privacy_audit_dead_letter_tenant_id on privacy_audit_dead_letter (tenant_id);
```

Use this model when:

- you already use JDBC repositories in production
- tenant-scoped audit/dead-letter reads are frequent
- you want a stronger production default without changing stable event/query carrier types

## Model 3: Custom Tenant-Aware Repository SPI

Relevant extension points:

- `PrivacyTenantAuditReadRepository`
- `PrivacyTenantAuditDeadLetterReadRepository`
- `PrivacyTenantAuditWriteRepository`
- `PrivacyTenantAuditDeadLetterWriteRepository`

Use this model when:

- tenant IDs live outside `details_json`
- you need tenant-partitioned tables, database views, row policies, or sharding
- you need write semantics that differ from the built-in repository behavior

Recommended ownership boundary:

- keep `PrivacyAuditEvent` and `PrivacyAuditDeadLetterEntry` unchanged
- treat tenant-aware read/write SPI as your repository-specific adaptation layer
- keep the helper services and management facade as the calling surface above your custom repositories

## Migration Recipes

### Recipe A: Detail-Key Only to JDBC Tenant Column

1. Confirm all tenant policies use one consistent detail key, for example `tenant`.
2. Add nullable tenant columns to audit and dead-letter tables.
3. Backfill existing rows from `details_json` only if tenant-scoped history queries matter for old data.
4. Add indexes on the tenant columns.
5. Enable `tenant-column-name` and `tenant-detail-key`.
6. Verify new writes populate the tenant column.
7. Validate tenant-scoped reads, stats, replay, export, and import paths.

### Recipe B: Built-In JDBC Model to Custom SPI

1. Freeze the tenant detail key contract first.
2. Implement tenant-aware read SPI while keeping built-in write behavior unchanged.
3. Compare results between built-in helper filtering and your native repository reads.
4. When reads are stable, add tenant-aware write SPI if needed.
5. Only then remove reliance on the built-in JDBC tenant-column behavior.

### Recipe C: Non-Web Jobs Joining the Tenant Model

1. Open a tenant scope explicitly with `PrivacyTenantContextHolder.openScope(...)`.
2. Capture snapshots before crossing threads with `PrivacyTenantContextSnapshot`.
3. Prefer wrapping the target `Executor` and any `CompletableFuture` callback functions from the snapshot instead of manually reopening scopes in every async stage.
4. Verify the same tenant detail key and JDBC tenant-column behavior apply to job-triggered writes.

## Acceptance Checklist

Before calling your tenant model rollout complete, verify:

1. Every write path produces the same tenant detail key.
2. JDBC rows show the expected tenant column values for new data.
3. Tenant-scoped query and stats endpoints return identical results before and after repository-native optimization.
4. Dead-letter replay preserves or restores tenant identity as expected.
5. Export/import flows still retag correctly when importing into a target tenant.
6. Async and buffered publishing preserve tenant context.
7. Your migration tool owns all non-bootstrap DDL and index changes.
8. `privacy.audit.tenant.read.path{domain=*,path=*}` reflects the expected native or fallback query path during rollout.
9. `privacy.audit.tenant.read.path{domain="dead_letter_export",path=*}` and `privacy.audit.tenant.read.path{domain="dead_letter_manifest",path=*}` reflect whether tenant-scoped exchange reads are reusing native repository filtering.
10. `privacy.audit.tenant.write.path{domain=*,path=*}` reflects the expected native or fallback persistence path during rollout.
11. `privacy.audit.tenant.write.path{domain="dead_letter_import",path=*}` reflects whether tenant-scoped import is using tenant-aware bulk persistence.
12. Tenant alert transitions and delivery metrics reflect the tenants and channels you expect to fan out.
13. Receiver route failure metrics reflect the expected path patterns for tenant-specific callback endpoints.
14. If you run more than one application instance locally, both nodes observe the same JDBC replay-store and tenant backlog state before you trust a shared production deployment.

## Anti-Patterns

Avoid these:

- mixing multiple tenant detail keys across policies in the same deployment
- enabling a custom tenant column name without updating schema migration scripts
- treating `details_json` and the tenant column as unrelated sources of truth
- adding custom repository SPI before measuring whether the built-in JDBC tenant column is actually insufficient

## Suggested Rollout Order

For most teams, the pragmatic order is:

1. Tenant detail tagging
2. Non-web tenant context propagation
3. Tenant-native read SPI and built-in JDBC tenant column
4. Tenant-scoped dead-letter observability policy overrides
5. Tenant-aware write SPI
6. Custom repository SPI only if needed

That order keeps the stable contracts intact while improving production behavior incrementally.

## Local Recipe

If you want a runnable local reference before touching your production application, use the sample profile:

- `samples/privacy-demo/src/main/resources/application-jdbc-tenant.yml`
- `samples/privacy-demo/src/main/resources/application-redis-tenant.yml`
- `samples/privacy-demo/src/main/resources/application-postgres-redis-tenant.yml`
- `samples/privacy-demo/src/main/resources/application-fallback-tenant.yml`
- `samples/privacy-demo/src/main/resources/application-custom-tenant-native.yml`
- `samples/privacy-demo/src/main/resources/application-custom-jdbc-tenant.yml`
- `samples/privacy-demo/src/main/resources/application-custom-jdbc-tenant-node2.yml`

Run it with:

- Windows: `mvnw.cmd -f samples/privacy-demo/pom.xml spring-boot:run -Dspring-boot.run.profiles=jdbc-tenant`
- macOS / Linux: `./mvnw -f samples/privacy-demo/pom.xml spring-boot:run -Dspring-boot.run.profiles=jdbc-tenant`

That profile demonstrates:

- JDBC audit and dead-letter repositories
- dedicated tenant columns with a non-default column name
- JDBC replay-store
- the existing tenant-aware sample endpoints on top of the JDBC path

If your rollout concern is shared receiver nonce protection rather than JDBC-backed audit storage, use the Redis sample profile instead:

- Windows: `mvnw.cmd -f samples/privacy-demo/pom.xml spring-boot:run -Dspring-boot.run.profiles=redis-tenant`
- macOS / Linux: `./mvnw -f samples/privacy-demo/pom.xml spring-boot:run -Dspring-boot.run.profiles=redis-tenant`

For a second local node, add:

- `redis-tenant-node2`

That path demonstrates a shared external replay-store without changing the audit/dead-letter persistence model used by the rest of the sample.
If you want the sample to provision Redis locally first, use:

- `powershell.exe -ExecutionPolicy Bypass -File samples/privacy-demo/scripts/manage-redis-local.ps1`

Then validate the two-node flow with:

- `powershell.exe -ExecutionPolicy Bypass -File samples/privacy-demo/scripts/verify-redis-tenant-multi-instance.ps1`

For a local two-node rollout rehearsal, start a second process with:

- Windows: `mvnw.cmd -f samples/privacy-demo/pom.xml spring-boot:run -Dspring-boot.run.profiles=jdbc-tenant,jdbc-tenant-node2`
- macOS / Linux: `./mvnw -f samples/privacy-demo/pom.xml spring-boot:run -Dspring-boot.run.profiles=jdbc-tenant,jdbc-tenant-node2`

That overlay keeps the same shared JDBC database and replay-store but moves the HTTP port and `demo.instance-id`, which makes it a simple local approximation of a multi-instance deployment.

If you want the same local rollout rehearsal without JDBC replay-store schema management, use the Redis-backed sample profiles:

- `samples/privacy-demo/src/main/resources/application-redis-tenant.yml`
- `samples/privacy-demo/src/main/resources/application-redis-tenant-node2.yml`

To prepare the local dependency itself, the sample also includes:

- `samples/privacy-demo/docker-compose.redis.yml`
- `samples/privacy-demo/scripts/manage-redis-local.ps1`
- `samples/privacy-demo/scripts/manage-redis-local.sh`

Those profiles are intended for environments that already run Redis and want shared nonce protection across nodes while keeping the rest of the tenant observability flow unchanged.

If you want a production-like local recipe with JDBC audit/dead-letter storage plus shared Redis replay-store protection, use:

- `samples/privacy-demo/src/main/resources/application-postgres-redis-tenant.yml`
- `samples/privacy-demo/src/main/resources/application-postgres-redis-tenant-node2.yml`
- `samples/privacy-demo/docker-compose.postgres-redis.yml`
- `samples/privacy-demo/scripts/manage-postgres-redis-local.ps1`
- `samples/privacy-demo/scripts/manage-postgres-redis-local.sh`
- `samples/privacy-demo/scripts/verify-postgres-redis-tenant-multi-instance.ps1`
- `samples/privacy-demo/scripts/verify-postgres-redis-tenant-multi-instance.sh`

This path keeps the same tenant observability surface, uses JDBC audit/dead-letter repositories against PostgreSQL, and keeps shared receiver nonce protection on Redis.

If your goal is not rollout rehearsal but validating the difference between tenant-native and helper-driven paths, use `fallback-tenant` as a comparison profile. It is not a production recommendation; it exists to prove that the `fallback` counters and repository capability reporting behave as documented.

If you want a sample-level reference for implementing the tenant repository SPI yourself, use `custom-tenant-native`. It keeps the same sample endpoints and observability surface, but swaps in sample-defined tenant-aware repositories so you can compare a custom native implementation against both the built-in profiles and the `fallback-tenant` contrast profile.

If you want the same idea with JDBC and real schema initialization still in the loop, use `custom-jdbc-tenant`. It is the sample's reference for “user-owned JDBC repository beans that still satisfy the tenant SPI and keep native tenant paths.”
For a second local node, pair it with `custom-jdbc-tenant-node2` and reuse the custom JDBC multi-instance verification scripts under `samples/privacy-demo/scripts/`.
