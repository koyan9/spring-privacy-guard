# GitHub Release Copy: v0.3.0

Use the content below directly in the GitHub Release editor if you do not want to paste `docs/releases/RELEASE_NOTES_v0.3.0.md` manually.

---

spring-privacy-guard v0.3.0

Highlights

- Baseline multi-tenant support with request-header tenant propagation, per-tenant masking policies, and tenant-scoped audit and dead-letter helpers
- Tenant-native read SPI for built-in in-memory and JDBC repositories so tenant helpers can pre-filter before fallback paging
- Optional dedicated JDBC tenant columns for built-in audit and dead-letter repositories
- Redis-backed webhook replay-store support for shared receiver nonce protection without JDBC schema management
- Tenant-aware management facade for audit query, stats, dead-letter query, replay, delete, export, and import flows
- Safer auto-configuration boundaries so optional JDBC and Redis replay-store support does not break applications that do not include those dependencies

Included Capabilities

Core

- PrivacyTenantProvider
- PrivacyTenantContextScope
- PrivacyTenantContextSnapshot
- PrivacyTenantPolicyResolver
- PrivacyTenantPolicy
- PrivacyTenantAwareMaskingStrategy
- PrivacyTenantContextHolder

Audit and Dead Letters

- PrivacyTenantAuditReadRepository
- PrivacyTenantAuditDeadLetterReadRepository
- PrivacyTenantAuditWriteRepository
- PrivacyTenantAuditDeadLetterWriteRepository
- PrivacyTenantAuditPolicy
- PrivacyTenantAuditPolicyResolver
- PrivacyTenantAuditQueryService
- PrivacyTenantAuditDeadLetterQueryService
- PrivacyTenantAuditDeadLetterOperationsService
- PrivacyTenantAuditDeadLetterExchangeService
- PrivacyTenantAuditManagementService

Receiver Verification and Replay Store

- RedisPrivacyAuditDeadLetterWebhookReplayStore
- Redis replay-store properties and auto-configuration
- classpath-safe JDBC and Redis replay-store wiring

Docs and Samples

- docs/MULTI_TENANT_GUIDE.md
- updated samples/privacy-demo tenant endpoints

Upgrade Notes

- No intentional breaking changes are included in v0.3.0
- Stable event and query contracts remain tenant-agnostic
- Built-in in-memory and JDBC repositories now implement tenant-native read SPI that helpers can prefer before fallback filtering
- Built-in repository publisher, async publisher, buffered publisher, and repository-backed dead-letter handler now propagate tenant-aware write hints through the new write SPI
- Built-in JDBC repositories can also persist and query tenant IDs through optional dedicated tenant columns when configured
- Non-web tenant propagation can use PrivacyTenantContextScope and PrivacyTenantContextSnapshot
- Redis replay-store support is selected behind JDBC and ahead of file storage when multiple backends are enabled
- The sample uses `X-Privacy-Tenant` for tenant selection and `X-Demo-Admin-Token` for sample-only protected management endpoints

Verification

- `mvnw.cmd -q test -pl privacy-guard-core,privacy-guard-spring-boot-starter` or `./mvnw -q test -pl privacy-guard-core,privacy-guard-spring-boot-starter`
- `mvnw.cmd -q verify` or `./mvnw -q verify`
- `mvnw.cmd -q -DskipTests install && mvnw.cmd -q -f samples/privacy-demo/pom.xml test`
- `python scripts/check_repo_hygiene.py`

---

Suggested release title:

`spring-privacy-guard v0.3.0`
