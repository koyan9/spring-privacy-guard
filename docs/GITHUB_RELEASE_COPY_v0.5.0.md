# GitHub Release Copy: v0.5.0

Use the content below directly in the GitHub Release editor if you do not want to paste `docs/releases/RELEASE_NOTES_v0.5.0.md` manually.

---

spring-privacy-guard v0.5.0

Highlights

- Completed tenant-aware single-entry dead-letter management across lookup, delete, and replay with explicit native capability reporting for `by-id` flows
- Expanded the tenant policy surface with stable tenant dead-letter alert route, delivery, and monitoring policy resolvers
- Added per-tenant control over dead-letter alert channel delivery and monitor membership without changing stable event or query contracts
- Strengthened the sample and observability surface so effective tenant alert policy state is visible in `/demo-tenants/policies` and `/demo-tenants/observability`

Included Capabilities

Multi-Tenant Policy and SPI

- PrivacyTenantDeadLetterAlertRoutePolicy
- PrivacyTenantDeadLetterAlertRoutePolicyResolver
- PrivacyTenantDeadLetterAlertDeliveryPolicy
- PrivacyTenantDeadLetterAlertDeliveryPolicyResolver
- PrivacyTenantDeadLetterAlertMonitoringPolicy
- PrivacyTenantDeadLetterAlertMonitoringPolicyResolver
- by-id tenant delete / replay capability flags for dead-letter repositories

Observability

- privacy.audit.tenant.read.path for dead_letter_find_by_id
- privacy.audit.tenant.write.path for dead_letter_delete_by_id / dead_letter_replay_by_id
- effective tenant alert delivery and membership state visible through the sample policy/observability endpoints

Upgrade Notes

- No intentional breaking changes are included in v0.5.0
- Global dead-letter alert properties remain valid
- Legacy tenant route properties remain supported and bridge into the new effective tenant alert route policy
- Tenant alert monitoring still requires the global tenant alert switch, but individual tenants can now be explicitly enabled or disabled through tenant policy

Verification

- `mvnw.cmd -q verify` or `./mvnw -q verify`
- `mvnw.cmd -q install` or `./mvnw -q install`
- `mvnw.cmd -q -f samples/privacy-demo/pom.xml test` or `./mvnw -q -f samples/privacy-demo/pom.xml test`
- `python scripts/check_repo_hygiene.py`

---

Suggested release title:

`spring-privacy-guard v0.5.0`
