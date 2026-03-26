# Release Announcement: v0.4.0

Use this document as the short external-facing announcement copy for the published `spring-privacy-guard v0.4.0` release.

## One-Line Summary

`spring-privacy-guard v0.4.0` expands the multi-tenant operating model with tenant-scoped dead-letter observability policy overrides, richer tenant telemetry, clearer native vs fallback visibility, and a broader rollout sample matrix.

## Key Highlights

- Added stable tenant logging policy and tenant dead-letter observability policy resolvers without changing the existing stable event and query contracts
- Added tenant alert transition metrics, delivery outcome metrics, receiver route failure metrics, and exchange-path telemetry for export, manifest, and import
- Completed the tenant-native dead-letter path across built-in repositories for read, write, delete, replay, and import flows
- Expanded the sample matrix with native, fallback, custom-native, custom-JDBC, custom-JDBC two-node, and PostgreSQL + Redis production-like references
- Added repository implementation and capability reporting to the sample observability endpoint so native vs fallback behavior can be validated directly

## Suggested GitHub / Community Post

We have released `spring-privacy-guard v0.4.0`.

This release candidate focuses on making the starter easier to evaluate and operate in multi-tenant deployments. It expands tenant policy coverage, adds clearer operational telemetry for dead-letter and receiver flows, and broadens the sample matrix so teams can compare built-in native, fallback, and custom repository implementations before rollout.

If you need privacy-safe masking, log sanitization, audit trails, dead-letter operations, receiver verification, and a clearer path to multi-tenant rollout validation in a Spring Boot starter, `v0.4.0` is the recommended upgrade after `v0.3.0`.

## Related Release Assets

- Release notes: `docs/releases/RELEASE_NOTES_v0.4.0.md`
- GitHub release copy: `docs/GITHUB_RELEASE_COPY_v0.4.0.md`
- Tenant observability guide: `docs/TENANT_OBSERVABILITY_GUIDE.md`
- Tenant adoption playbook: `docs/TENANT_ADOPTION_PLAYBOOK.md`
- Release runbook: `docs/RELEASE_RUNBOOK_v0.4.0.md`
