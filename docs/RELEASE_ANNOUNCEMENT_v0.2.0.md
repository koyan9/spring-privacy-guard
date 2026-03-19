# Release Announcement: v0.2.0

Use this document as the short external-facing announcement for `spring-privacy-guard v0.2.0`.

## One-Line Summary

`spring-privacy-guard v0.2.0` adds stable SPI markers, stronger audit and dead-letter operations, JDBC rollout guidance, signed alert delivery, receiver verification, and richer observability for privacy-sensitive Spring Boot applications.

## Key Highlights

- Added `@StableSpi` markers for supported extension points such as `MaskingStrategy`, alert callbacks, repositories, and replay-store contracts
- Added configurable text-pattern masking plus MDC and structured logging sanitization coverage
- Added async and batched audit publishing with retry, dead-letter fallback, and configurable executor concurrency
- Expanded dead-letter operations with query, replay, cleanup, export/import, and checksum validation
- Added JDBC production guidance for audit, dead-letter, and replay-store rollout and migration
- Added built-in webhook and email alert callbacks plus signed receiver verification with filter and interceptor integration modes
- Improved Actuator and Micrometer visibility for webhook failures, replay-store cleanup, and verification reason codes

## Suggested GitHub / Community Post

We have released `spring-privacy-guard v0.2.0`.

This release focuses on making privacy-safe operations easier to extend and operate in production. It stabilizes the main extension surface with `@StableSpi`, adds a JDBC production guide for audit, dead-letter, and replay-store rollout, improves audit publishing with async and batched delivery, strengthens dead-letter handling, and adds built-in webhook and email alert callbacks. It also adds signed receiver verification and richer observability with health indicators and metrics.

If you are already using Spring Boot and need field masking, log sanitization, privacy audit trails, and operational dead-letter workflows in one starter, `v0.2.0` is the recommended upgrade.

## Related Release Assets

- Release notes: `docs/releases/RELEASE_NOTES_v0.2.0.md`
- GitHub release copy: `docs/GITHUB_RELEASE_COPY_v0.2.0.md`
- JDBC production guide: `docs/JDBC_PRODUCTION_GUIDE.md`
- Release runbook: `docs/RELEASE_RUNBOOK_v0.2.0.md`
