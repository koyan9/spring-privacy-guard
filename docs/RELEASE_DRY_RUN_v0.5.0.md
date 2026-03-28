# Release Dry Run: v0.5.0

Release-preparation dry-run record for the published `v0.5.0` release.

## Scope Reviewed

- Root docs: `README.md`, `README.zh-CN.md`, `CHANGELOG.md`
- Multi-tenant docs: `docs/MULTI_TENANT_GUIDE.md`, `docs/TENANT_OBSERVABILITY_GUIDE.md`
- Sample docs: `samples/privacy-demo/README.md`
- Release materials: `docs/releases/RELEASE_NOTES_v0.5.0.md`, `docs/GITHUB_RELEASE_COPY_v0.5.0.md`
- Release announcements: `docs/RELEASE_ANNOUNCEMENT_v0.5.0.md`, `docs/RELEASE_ANNOUNCEMENT_v0.5.0.zh-CN.md`
- Release workflow: `.github/workflows/release.yml`

## Checklist Result

### Code & Tests

- [x] `mvnw.cmd -q install` passes locally for the published `v0.5.0` release baseline
- [x] focused starter tests for tenant alert route, delivery, monitoring, and by-id dead-letter management pass locally
- [x] focused sample tests for policy/observability views and by-id dead-letter flows pass locally

### Docs

- [x] `README.md` updated
- [x] `README.zh-CN.md` updated where needed
- [x] `samples/privacy-demo/README.md` updated
- [x] `CHANGELOG.md` updated
- [x] release notes finalized in `docs/releases/RELEASE_NOTES_v0.5.0.md`
- [x] execution guide, runbook, and GitHub release copy finalized for `v0.5.0`
- [x] release announcement finalized in both English and zh-CN for `v0.5.0`

### Release Content

- [x] configuration metadata updated for tenant alert route, delivery, and monitoring policy properties
- [x] sample policy / observability views updated to expose effective tenant alert policy state
- [x] sample by-id dead-letter capability and telemetry coverage updated
- [x] publishable module versions aligned to `0.5.0`

## Notes

- The published `v0.5.0` scope consolidates the post-`v0.4.0` tenant alert policy expansion work and the final by-id tenant-native dead-letter management path.
- Final release validation should explicitly confirm that the sample policy view, tenant alerting membership list, and by-id write-path telemetry match the documented expectations.
- Local draft validation was completed on 2026-03-28 with:
  - `mvnw.cmd -q install`
  - `mvnw.cmd -q -pl privacy-guard-spring-boot-starter -Dtest=PrivacyTenantAuditDeadLetterOperationsServiceTest,MicrometerPrivacyTenantAuditTelemetryTest test`
  - `mvnw.cmd -q -pl privacy-guard-spring-boot-starter -Dtest=LoggingPrivacyTenantAuditDeadLetterAlertCallbackTest,TenantScopedPrivacyAuditDeadLetterWebhookAlertCallbackTest,TenantScopedPrivacyAuditDeadLetterEmailAlertCallbackTest,PrivacyGuardDeadLetterObservabilityAutoConfigurationTest,PrivacyGuardAutoConfigurationTest test`
  - `mvnw.cmd -q -f samples/privacy-demo/pom.xml -Dtest=PrivacyDemoApplicationTest,PrivacyDemoDefaultDeadLetterHttpTest,PrivacyDemoJdbcTenantDeadLetterMaterializationHttpTest,PrivacyDemoJdbcTenantProfileTest,PrivacyDemoPostgresRedisTenantProfileTest,PrivacyDemoFallbackTenantProfileTest,PrivacyDemoCustomTenantNativeProfileTest,PrivacyDemoCustomJdbcTenantProfileTest,PrivacyDemoDefaultReceiverHttpTest test`
  - `python scripts/check_repo_hygiene.py`
