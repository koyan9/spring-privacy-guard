# Release Announcement: v0.3.0

Use this document as the short external-facing announcement copy for the published `spring-privacy-guard v0.3.0` release.

## One-Line Summary

`spring-privacy-guard v0.3.0` adds baseline multi-tenant privacy policies, Redis-backed receiver replay-store support, tenant-aware audit and dead-letter management helpers, and safer optional auto-configuration boundaries.

## Key Highlights

- Added request-header tenant propagation with per-tenant masking and text-policy resolution
- Added `PrivacyTenantAwareMaskingStrategy` and tenant-aware audit detail policies without changing the stable event and query contracts
- Added tenant-native read SPI for built-in in-memory and JDBC repositories so tenant helpers can pre-filter before fallback paging
- Added tenant-scoped audit query, dead-letter query, replay, delete, export, import, and unified management helpers
- Added Redis-backed replay-store support for signed alert receivers that need shared nonce protection without JDBC schema management
- Fixed receiver auto-configuration so optional JDBC and Redis replay-store dependencies do not cause startup failures when absent
- Added dedicated multi-tenant and JDBC production rollout guides for operators and adopters

## Suggested GitHub / Community Post

We have released `spring-privacy-guard v0.3.0`.

This release focuses on making the starter easier to operate in shared environments. It adds baseline multi-tenant support for masking, text sanitization, and audit/dead-letter management, introduces a Redis-backed replay store for signed alert receivers, and tightens the optional auto-configuration boundaries around JDBC and Redis integrations.

If you need privacy-safe field masking, log sanitization, audit trails, dead-letter operations, and verified receiver flows in a Spring Boot starter, `v0.3.0` is the recommended upgrade from `v0.2.0`.

## Related Release Assets

- Release notes: `docs/releases/RELEASE_NOTES_v0.3.0.md`
- GitHub release copy: `docs/GITHUB_RELEASE_COPY_v0.3.0.md`
- Multi-tenant guide: `docs/MULTI_TENANT_GUIDE.md`
- JDBC production guide: `docs/JDBC_PRODUCTION_GUIDE.md`
- Release runbook: `docs/RELEASE_RUNBOOK_v0.3.0.md`
