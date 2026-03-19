# spring-privacy-guard v0.3.0

## Highlights

- Baseline multi-tenant support with request-header tenant propagation, per-tenant masking policies, and tenant-scoped audit and dead-letter helpers
- Tenant-native read SPI for built-in in-memory and JDBC repositories so tenant helpers can pre-filter before fallback paging
- Optional dedicated JDBC tenant columns for built-in audit and dead-letter repositories
- Redis-backed webhook replay-store support for shared receiver nonce protection without JDBC schema management
- Tenant-aware management facade for audit query, stats, dead-letter query, replay, delete, export, and import flows
- Safer auto-configuration boundaries so optional JDBC and Redis replay-store support does not break applications that do not include those dependencies

## Included Capabilities

### Core

- `PrivacyTenantProvider`
- `PrivacyTenantContextScope`
- `PrivacyTenantContextSnapshot`
- `PrivacyTenantPolicyResolver`
- `PrivacyTenantPolicy`
- `PrivacyTenantAwareMaskingStrategy`
- `PrivacyTenantContextHolder`
- tenant-aware `MaskingService` and `TextMaskingService`

### Audit and Dead Letters

- `PrivacyTenantAuditPolicy`
- `PrivacyTenantAuditPolicyResolver`
- `PrivacyTenantAuditReadRepository`
- `PrivacyTenantAuditDeadLetterReadRepository`
- `PrivacyTenantAuditWriteRepository`
- `PrivacyTenantAuditDeadLetterWriteRepository`
- `PrivacyTenantAuditQueryService`
- `PrivacyTenantAuditDeadLetterQueryService`
- `PrivacyTenantAuditDeadLetterOperationsService`
- `PrivacyTenantAuditDeadLetterExchangeService`
- `PrivacyTenantAuditManagementService`

### Receiver Verification and Replay Store

- `RedisPrivacyAuditDeadLetterWebhookReplayStore`
- Redis replay-store configuration properties and auto-configuration
- classpath-safe JDBC and Redis replay-store wiring in receiver auto-configuration

### Docs and Samples

- `docs/MULTI_TENANT_GUIDE.md`
- updated `docs/JDBC_PRODUCTION_GUIDE.md`
- tenant-aware sample endpoints in `samples/privacy-demo`

## Upgrade Notes

- No intentional breaking changes are included in `v0.3.0`
- Stable event and query contracts remain tenant-agnostic: `PrivacyAuditEvent`, `PrivacyAuditQueryCriteria`, and `PrivacyAuditDeadLetterQueryCriteria` are unchanged
- Tenant-scoped filtering is layered through helper and facade services rather than by adding tenant fields to the stable criteria records
- Built-in in-memory and JDBC repositories now implement tenant-native read SPI that helpers can prefer before falling back to cross-page filtering
- Built-in repository publisher, async publisher, buffered publisher, and repository-backed dead-letter handler now propagate tenant-aware write hints through the new write SPI
- Built-in JDBC repositories can also persist and query tenant IDs through optional dedicated tenant columns when configured
- Non-web tenant propagation can now use `PrivacyTenantContextScope` and `PrivacyTenantContextSnapshot` instead of manually juggling `ThreadLocal` state
- Redis replay-store support requires the optional Redis dependencies on the application classpath and is selected ahead of file storage but behind JDBC when multiple replay-store backends are enabled
- The sample app now demonstrates two headers with different purposes:
  - `X-Privacy-Tenant` for runtime tenant selection
  - `X-Demo-Admin-Token` for sample-only protected management endpoints

## Verification

- `mvnw.cmd -q test -pl privacy-guard-core,privacy-guard-spring-boot-starter` or `./mvnw -q test -pl privacy-guard-core,privacy-guard-spring-boot-starter`
- `mvnw.cmd -q verify` or `./mvnw -q verify`
- `mvnw.cmd -q -DskipTests install && mvnw.cmd -q -f samples/privacy-demo/pom.xml test`
- `python scripts/check_repo_hygiene.py`

## Release Checklist

- Drafted from the `0.3.0` section in `CHANGELOG.md`
- Reviewed against `docs/RELEASE_CHECKLIST.md`
- Intended for tag `v0.3.0`
