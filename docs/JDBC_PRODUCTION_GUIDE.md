# JDBC Production Guide

This guide covers production use of the JDBC-backed audit repository, dead-letter repository, and receiver replay store for `spring-privacy-guard`.

## Scope

The starter supports three JDBC-backed persistence areas:

1. Audit events
   Property prefix: `privacy.guard.audit.*`
   Default table: `privacy_audit_event`
2. Dead letters
   Property prefix: `privacy.guard.audit.dead-letter.*`
   Default table: `privacy_audit_dead_letter`
3. Receiver replay store
   Property prefix: `privacy.guard.audit.dead-letter.observability.alert.receiver.replay-store.jdbc.*`
   Default table: `privacy_audit_webhook_replay_store`

Audit and dead-letter JDBC repositories can also use an optional dedicated tenant column:

- `privacy.guard.audit.jdbc.tenant-column-name`
- `privacy.guard.audit.jdbc.tenant-detail-key`
- `privacy.guard.audit.dead-letter.jdbc.tenant-column-name`
- `privacy.guard.audit.dead-letter.jdbc.tenant-detail-key`

## Recommended Production Approach

Use JDBC when you need any of the following:

- Shared state across multiple application instances
- Durable audit or dead-letter retention
- SQL-based operations, backup, and observability
- Receiver replay protection that survives restarts

For production deployments:

- Prefer schema management through Flyway, Liquibase, or a DBA-controlled migration pipeline
- Use `*.initialize-schema=true` only for local bootstrap, smoke tests, or the first controlled startup
- Turn `*.initialize-schema=false` after the schema is created and versioned
- Set the explicit dialect when auto-detection is not reliable

## Configuration Examples

PostgreSQL example:

```yaml
privacy:
  guard:
    audit:
      repository-type: JDBC
      jdbc:
        initialize-schema: false
        dialect: POSTGRESQL
        table-name: privacy_audit_event
        tenant-column-name: tenant_id
        tenant-detail-key: tenant
      dead-letter:
        repository-type: JDBC
        jdbc:
          initialize-schema: false
          dialect: POSTGRESQL
          table-name: privacy_audit_dead_letter
          tenant-column-name: tenant_id
          tenant-detail-key: tenant
        observability:
          alert:
            receiver:
              replay-store:
                jdbc:
                  enabled: true
                  initialize-schema: false
                  dialect: POSTGRESQL
                  table-name: privacy_audit_webhook_replay_store
                  cleanup-interval: 5m
                  cleanup-batch-size: 500
```

MySQL example:

```yaml
privacy:
  guard:
    audit:
      repository-type: JDBC
      jdbc:
        initialize-schema: false
        dialect: MYSQL
        tenant-column-name: tenant_id
        tenant-detail-key: tenant
      dead-letter:
        repository-type: JDBC
        jdbc:
          initialize-schema: false
          dialect: MYSQL
          tenant-column-name: tenant_id
          tenant-detail-key: tenant
        observability:
          alert:
            receiver:
              replay-store:
                jdbc:
                  enabled: true
                  initialize-schema: false
                  dialect: MYSQL
                  cleanup-interval: 5m
                  cleanup-batch-size: 500
```

## Built-In Schema Resources

Audit repository:

- `classpath:META-INF/privacy-guard/privacy-audit-schema-generic.sql`
- `classpath:META-INF/privacy-guard/privacy-audit-schema-h2.sql`
- `classpath:META-INF/privacy-guard/privacy-audit-schema-postgresql.sql`
- `classpath:META-INF/privacy-guard/privacy-audit-schema-mysql.sql`

Dead-letter repository:

- `classpath:META-INF/privacy-guard/privacy-audit-dead-letter-schema-generic.sql`
- `classpath:META-INF/privacy-guard/privacy-audit-dead-letter-schema-h2.sql`
- `classpath:META-INF/privacy-guard/privacy-audit-dead-letter-schema-postgresql.sql`
- `classpath:META-INF/privacy-guard/privacy-audit-dead-letter-schema-mysql.sql`

Replay store:

- `classpath:META-INF/privacy-guard/privacy-audit-dead-letter-webhook-replay-store-schema-generic.sql`
- `classpath:META-INF/privacy-guard/privacy-audit-dead-letter-webhook-replay-store-schema-h2.sql`
- `classpath:META-INF/privacy-guard/privacy-audit-dead-letter-webhook-replay-store-schema-postgresql.sql`
- `classpath:META-INF/privacy-guard/privacy-audit-dead-letter-webhook-replay-store-schema-mysql.sql`

You can override any of them with `*.schema-location`.

## MySQL and PostgreSQL Differences

Current built-in schema differences worth noting:

- Audit `details_json`
  PostgreSQL uses `text`
  MySQL uses `longtext`
- Dead-letter timestamps
  PostgreSQL uses `timestamp`
  MySQL uses `datetime(6)` for `failed_at` and `occurred_at`
- Replay store timestamps
  PostgreSQL uses `timestamp`
  MySQL uses `datetime(6)`
- Replay store indexing
  Both built-in variants add an `expires_at` index
- Audit and dead-letter tenant column
  Built-in schemas now include an optional nullable `tenant_id` column for adopters that configure a dedicated tenant column in JDBC mode

The starter does not currently create additional query indexes for audit and dead-letter tables. This is intentional because the right indexes depend on your workload.

## Recommended Indexes

The built-in schema is enough to start, but production query workloads usually need extra indexes.

Audit table recommendations:

- Index `occurred_at` for time-window queries
- Index `(resource_type, resource_id)` when resource lookups are common
- Index `tenant_id` when you enable the dedicated tenant column and tenant-scoped queries are common
- Index `actor` if operator-centric review is common
- Index `action` or `outcome` only when those filters are frequent and selective

Dead-letter table recommendations:

- Index `failed_at` for backlog and replay operations
- Index `(resource_type, resource_id)` for resource-centric support flows
- Index `tenant_id` when you enable the dedicated tenant column and tenant-scoped dead-letter workflows are common
- Index `error_type` if triage is usually grouped by failure category
- Index `occurred_at` if you query by original event time

Replay store recommendations:

- Keep the built-in `expires_at` index
- Do not add wide composite indexes unless you have measured a real query need

## Migration Notes

### First-Time JDBC Enablement

Recommended order:

1. Create the target schema outside the application, or enable `initialize-schema` for a single controlled startup
2. Verify the application starts with JDBC enabled and writes new rows successfully
3. Disable `initialize-schema`
4. Add database monitoring and backup coverage before scaling traffic

### Migrating Audit or Dead-Letter Storage to JDBC

Common migration path:

1. Enable JDBC persistence in a lower environment with production-like queries
2. Create the schema and validate table names, dialect, and permissions
3. Switch repository type from `IN_MEMORY` or `NONE` to `JDBC`
4. Validate write path first, then query and replay path
5. Backfill historical records only if your operational model requires it

Notes:

- There is no built-in historical backfill from `IN_MEMORY`
- Dead-letter JSON/CSV export-import can be used for controlled data migration when needed
- For large imports, prefer the paged/streaming import APIs rather than loading files fully in memory

### Enabling the Dedicated Tenant Column

Recommended order:

1. Add the nullable tenant column to your audit and dead-letter tables, for example `tenant_id varchar(255)`
2. Add an index on that column if tenant-scoped reads are part of your main workload
3. Configure `tenant-column-name` and `tenant-detail-key`
4. Verify new writes populate the column as expected
5. Switch tenant-heavy read paths to rely on the native repository filtering

Notes:

- The starter keeps tenant information in `details_json` regardless, so the dedicated column is an optimization and isolation aid rather than the sole source of truth
- To avoid partial population, use a consistent tenant detail key across your tenant audit policies when you enable the dedicated column

### Migrating Receiver Replay Store from File to JDBC

Recommended order:

1. Create the JDBC replay-store table
2. Enable JDBC replay store in one environment
3. Rotate receiver secrets if you want to invalidate all previously accepted nonces
4. Cut traffic over to the JDBC-backed deployment
5. Remove or archive the old file store after validation

Notes:

- Existing replay nonces are not automatically imported from file storage
- During cutover, old nonces may still be accepted unless you rotate bearer token or signature secret

## Schema Change Strategy

Treat built-in SQL files as bootstrap references, not as your long-term migration system.

For production schema evolution:

- Version every table change in Flyway or Liquibase
- Keep DDL for MySQL and PostgreSQL aligned when the Java model is shared
- Roll out additive changes first
- Avoid destructive column changes without a compatibility window
- Keep `${tableName}` substitutions consistent with your configured table names

If you add indexes outside the starter, keep them in your migration tool rather than patching the built-in SQL resources directly.

## Validation Checklist

Before production rollout, verify:

1. The configured dialect matches the actual database
2. The application user can create, read, update, and delete the required rows
3. Audit writes succeed under expected throughput
4. Dead-letter replay and delete operations work against the target database
5. Replay-store cleanup keeps pace with your webhook traffic
6. Slow-query monitoring covers audit and dead-letter query patterns

## Related Docs

- `README.md`
- `docs/RECEIVER_OPERATIONS.md`
- `docs/LOGIC_WALKTHROUGH.md`
- `docs/TENANT_ADOPTION_PLAYBOOK.md`
- `samples/privacy-demo/README.md`
