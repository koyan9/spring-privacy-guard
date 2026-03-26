# spring-privacy-guard v0.4.0

Draft release notes for the next versioned release after `v0.3.0`.
Update dates, version numbers, and the final scope before tagging.

## Highlights

- Expanded the multi-tenant policy surface beyond masking and audit-detail filtering with stable tenant logging policy support and tenant-scoped dead-letter observability policy overrides.
- Expanded tenant observability with alert transition metrics, delivery outcome metrics, receiver route failure counters, and exchange-path telemetry for export, manifest, and import.
- Completed the tenant-native dead-letter management path for built-in repositories across read, write, delete, replay, and import flows, while making native vs fallback behavior visible in the sample.
- Added a broader sample matrix covering built-in native, fallback contrast, custom native, custom JDBC native, custom JDBC two-node rehearsal, and PostgreSQL + Redis production-like rehearsal.

## Included Capabilities

### Multi-Tenant Policy and SPI

- `PrivacyTenantLoggingPolicy`
- `PrivacyTenantLoggingPolicyResolver`
- `PrivacyTenantDeadLetterObservabilityPolicy`
- `PrivacyTenantDeadLetterObservabilityPolicyResolver`
- `PrivacyTenantAuditDeadLetterDeleteRepository`
- `PrivacyTenantAuditDeadLetterReplayRepository`

### Observability

- `privacy.audit.deadletters.alert.tenant.transitions{tenant=*,state=*,recovery=*}`
- `privacy.audit.deadletters.alert.tenant.deliveries{tenant=*,channel=*,outcome=*}`
- `privacy.audit.deadletters.receiver.route.failures{route=*,reason=*}`
- `privacy.audit.tenant.read.path{domain=dead_letter_export|dead_letter_manifest,path=*}`
- `privacy.audit.tenant.write.path{domain=dead_letter_import,path=*}`

### Sample and Rollout Matrix

| Profile | Purpose | Expected Path Shape |
| --- | --- | --- |
| default | Built-in in-memory baseline | native |
| `fallback-tenant` | Generic-only contrast profile | fallback |
| `custom-tenant-native` | Sample-owned custom tenant SPI reference | native |
| `jdbc-tenant` | Built-in JDBC tenant reference | native |
| `custom-jdbc-tenant` | Sample-owned custom JDBC tenant reference | native |
| `custom-jdbc-tenant,custom-jdbc-tenant-node2` | Two-node custom JDBC rehearsal | native |
| `postgres-redis-tenant,postgres-redis-tenant-node2` | Production-like PostgreSQL + Redis rehearsal | native |

## Upgrade Notes

- No intentional breaking changes are planned in `v0.4.0`.
- Stable event and query contracts remain tenant-agnostic.
- Existing stable carrier types remain unchanged; tenant observability overrides are introduced as sibling policy/resolver types.
- Tenant dead-letter import now prefers tenant-aware bulk persistence when the repository implements `PrivacyTenantAuditDeadLetterWriteRepository`.
- Tenant dead-letter export and manifest now emit dedicated exchange-path read telemetry so native and fallback repository behavior can be distinguished from normal query/stats paths.
- The sample now exposes repository implementation names and capability flags through `/demo-tenants/observability`, which is useful when validating built-in, fallback, and custom SPI paths.

## Validation

- `./mvnw -q verify` or `mvnw.cmd -q verify`
- `./mvnw -q -DskipTests install` or `mvnw.cmd -q -DskipTests install`
- `./mvnw -q -f samples/privacy-demo/pom.xml test` or `mvnw.cmd -q -f samples/privacy-demo/pom.xml test`
- `python scripts/check_repo_hygiene.py`

## Release Checklist

- Drafted from the `0.4.0` section in `CHANGELOG.md`
- Reviewed against `docs/RELEASE_CHECKLIST.md`
- Intended for tag `v0.4.0`
