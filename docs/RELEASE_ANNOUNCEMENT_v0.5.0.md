# Release Announcement: v0.5.0

Use this document as the short external-facing announcement copy for the published `spring-privacy-guard v0.5.0` release.

## One-Line Summary

`spring-privacy-guard v0.5.0` completes tenant-aware single-entry dead-letter management, expands tenant alert policy control across route, delivery, and monitor membership, and makes the effective tenant policy state easier to verify in the sample.

## Key Highlights

- Completed tenant-aware single-entry dead-letter management across lookup, delete, and replay with explicit native capability reporting for `by-id` flows
- Expanded the tenant policy surface with stable dead-letter alert route, delivery, and monitoring policy resolvers
- Added per-tenant control over whether logging, webhook, and email alert delivery are enabled
- Added per-tenant control over whether a tenant participates in the tenant alert monitor, instead of relying only on the global monitored-tenant allowlist
- Expanded the sample policy and observability views so teams can validate effective tenant alert route, delivery, and membership state directly

## Suggested GitHub / Community Post

We have released `spring-privacy-guard v0.5.0`.

This release focuses on finishing the tenant-aware dead-letter management path and making tenant alerting significantly more configurable without changing stable event or query contracts. Teams can now distinguish criteria-based and single-entry native mutation paths, route alerts per tenant, toggle logging/webhook/email delivery per tenant, and even control tenant alert-monitor membership through tenant policy.

If you are using `spring-privacy-guard` in shared or multi-tenant Spring Boot deployments, `v0.5.0` makes rollout validation clearer and alert behavior easier to reason about before production.

## Related Release Assets

- Release notes: `docs/releases/RELEASE_NOTES_v0.5.0.md`
- GitHub release copy: `docs/GITHUB_RELEASE_COPY_v0.5.0.md`
- Multi-tenant guide: `docs/MULTI_TENANT_GUIDE.md`
- Tenant observability guide: `docs/TENANT_OBSERVABILITY_GUIDE.md`
- Sample guide: `samples/privacy-demo/README.md`
- Release runbook: `docs/RELEASE_RUNBOOK_v0.5.0.md`
