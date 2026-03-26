# GitHub Release Copy: v0.4.0

Use the content below directly in the GitHub Release editor if you do not want to paste `docs/releases/RELEASE_NOTES_v0.4.0.md` manually.

---

spring-privacy-guard v0.4.0

Highlights

- Expanded the multi-tenant policy surface with stable tenant logging policy support and tenant-scoped dead-letter observability policy overrides
- Expanded tenant observability with alert transition metrics, delivery outcome metrics, receiver route failure counters, and exchange-path telemetry for export, manifest, and import
- Completed the tenant-native dead-letter management path across read, write, delete, replay, and import flows for built-in repositories
- Added a broader sample matrix covering built-in native, fallback contrast, custom native, custom JDBC native, custom JDBC two-node rehearsal, and PostgreSQL + Redis production-like rehearsal

Included Capabilities

Multi-Tenant Policy and SPI

- PrivacyTenantLoggingPolicy
- PrivacyTenantLoggingPolicyResolver
- PrivacyTenantDeadLetterObservabilityPolicy
- PrivacyTenantDeadLetterObservabilityPolicyResolver
- PrivacyTenantAuditDeadLetterDeleteRepository
- PrivacyTenantAuditDeadLetterReplayRepository

Observability

- privacy.audit.deadletters.alert.tenant.transitions
- privacy.audit.deadletters.alert.tenant.deliveries
- privacy.audit.deadletters.receiver.route.failures
- privacy.audit.tenant.read.path for dead_letter_export / dead_letter_manifest
- privacy.audit.tenant.write.path for dead_letter_import

Sample Matrix

- built-in native profiles
- fallback contrast profile
- custom tenant-native profile
- custom JDBC native profile
- custom JDBC two-node profile
- PostgreSQL + Redis production-like two-node profile

Upgrade Notes

- No intentional breaking changes are included in v0.4.0
- Stable event and query contracts remain tenant-agnostic
- Existing stable carrier types remain unchanged
- Tenant dead-letter import now prefers tenant-aware bulk persistence when the repository implements the tenant write SPI
- The sample now exposes repository implementation names and capability flags through `/demo-tenants/observability`

Verification

- `mvnw.cmd -q test -pl privacy-guard-core,privacy-guard-spring-boot-starter` or `./mvnw -q test -pl privacy-guard-core,privacy-guard-spring-boot-starter`
- `mvnw.cmd -q verify` or `./mvnw -q verify`
- `mvnw.cmd -q -DskipTests install && mvnw.cmd -q -f samples/privacy-demo/pom.xml test`
- `python scripts/check_repo_hygiene.py`

---

Suggested release title:

`spring-privacy-guard v0.4.0`
