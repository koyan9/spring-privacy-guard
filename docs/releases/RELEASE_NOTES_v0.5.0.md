# spring-privacy-guard v0.5.0

Release notes for the published `spring-privacy-guard v0.5.0` release.

## Highlights

- Completed tenant-aware single-entry dead-letter management across lookup, delete, and replay with explicit native capability reporting for `by-id` flows.
- Expanded the tenant policy surface again with stable tenant dead-letter alert route, delivery, and monitoring policy resolvers.
- Made tenant alerting more production-friendly by allowing per-tenant control over route targets, channel delivery, and alert-monitor membership without changing stable event or query contracts.
- Strengthened the sample and observability surface so default, JDBC, custom, and production-like profiles expose native vs fallback paths and effective tenant alert policy state more clearly.

## Included Capabilities

### Multi-Tenant Policy and SPI

- `PrivacyTenantDeadLetterAlertRoutePolicy`
- `PrivacyTenantDeadLetterAlertRoutePolicyResolver`
- `PrivacyTenantDeadLetterAlertDeliveryPolicy`
- `PrivacyTenantDeadLetterAlertDeliveryPolicyResolver`
- `PrivacyTenantDeadLetterAlertMonitoringPolicy`
- `PrivacyTenantDeadLetterAlertMonitoringPolicyResolver`
- `PrivacyTenantAuditDeadLetterDeleteRepository.supportsTenantDeleteById()`
- `PrivacyTenantAuditDeadLetterReplayRepository.supportsTenantReplayById()`

### Dead-Letter Management and Telemetry

- `privacy.audit.tenant.read.path{domain=dead_letter_find_by_id,path=*}`
- `privacy.audit.tenant.write.path{domain=dead_letter_delete_by_id,path=*}`
- `privacy.audit.tenant.write.path{domain=dead_letter_replay_by_id,path=*}`
- expanded sample capability reporting for `tenantDeleteByIdNative` / `tenantReplayByIdNative`

### Sample and Rollout Coverage

- default sample now exposes tenant-specific receiver routes and tenant alert delivery/membership overrides through `/demo-tenants/policies`
- focused sample HTTP coverage for tenant-scoped single-entry delete and replay in default and JDBC profiles
- JDBC / PostgreSQL+Redis / custom tenant profiles expose by-id native capability and telemetry through `/demo-tenants/observability`

## Upgrade Notes

- No intentional breaking changes are included in `v0.5.0`.
- Existing global dead-letter alert properties remain valid.
- Legacy `privacy.guard.audit.dead-letter.observability.alert.tenant.routes.<tenantId>.*` remains supported and bridges into the new effective tenant alert route policy.
- Tenant alert monitoring still requires the global `privacy.guard.audit.dead-letter.observability.alert.tenant.enabled=true` switch, but per-tenant membership can now be overridden through tenant policy.
- Built-in in-memory and JDBC dead-letter repositories now distinguish criteria-based mutation from single-entry `by-id` mutation in capability reporting and write-path telemetry.

## Validation

- `./mvnw -q verify` or `mvnw.cmd -q verify`
- `./mvnw -q install` or `mvnw.cmd -q install`
- `./mvnw -q -f samples/privacy-demo/pom.xml test` or `mvnw.cmd -q -f samples/privacy-demo/pom.xml test`
- Focused starter regressions:
  - `./mvnw -q -pl privacy-guard-spring-boot-starter -Dtest=PrivacyTenantAuditDeadLetterOperationsServiceTest,MicrometerPrivacyTenantAuditTelemetryTest test`
  - `./mvnw -q -pl privacy-guard-spring-boot-starter -Dtest=LoggingPrivacyTenantAuditDeadLetterAlertCallbackTest,TenantScopedPrivacyAuditDeadLetterWebhookAlertCallbackTest,TenantScopedPrivacyAuditDeadLetterEmailAlertCallbackTest,PrivacyGuardDeadLetterObservabilityAutoConfigurationTest,PrivacyGuardAutoConfigurationTest test`
- Focused sample regressions:
  - `./mvnw -q -f samples/privacy-demo/pom.xml -Dtest=PrivacyDemoApplicationTest,PrivacyDemoDefaultDeadLetterHttpTest,PrivacyDemoJdbcTenantDeadLetterMaterializationHttpTest,PrivacyDemoDefaultReceiverHttpTest test`
- `python scripts/check_repo_hygiene.py`

## Release Checklist

- Published from the `0.5.0` section in `CHANGELOG.md`
- Reviewed against `docs/RELEASE_CHECKLIST.md`
- Intended for tag `v0.5.0`
