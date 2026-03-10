# Release Announcement: v0.2.0

Use this document as the short external-facing announcement for `spring-privacy-guard v0.2.0`.

## One-Line Summary

`spring-privacy-guard v0.2.0` adds customizable masking, stronger audit and dead-letter operations, signed alert delivery, receiver verification, and richer observability for privacy-sensitive Spring Boot applications.

## Key Highlights

- Added a custom masking SPI with `MaskingStrategy` and `MaskingContext`
- Added async and batched audit publishing with retry and dead-letter fallback
- Expanded dead-letter operations with query, replay, cleanup, export/import, and checksum validation
- Added built-in webhook and email alert callbacks for dead-letter threshold notifications
- Added signed receiver verification with filter and interceptor integration modes
- Improved Actuator and Micrometer visibility for sender and receiver flows

## Suggested GitHub / Community Post

We have released `spring-privacy-guard v0.2.0`.

This release focuses on making privacy-safe operations easier to extend and operate in production. It introduces a custom masking SPI, improves audit publishing with async and batched delivery, strengthens dead-letter handling, and adds built-in webhook and email alert callbacks. It also adds signed receiver verification and richer observability with health indicators and metrics.

If you are already using Spring Boot and need field masking, log sanitization, privacy audit trails, and operational dead-letter workflows in one starter, `v0.2.0` is the recommended upgrade.

## Related Release Assets

- Release notes: `docs/releases/RELEASE_NOTES_v0.2.0.md`
- GitHub release copy: `docs/GITHUB_RELEASE_COPY_v0.2.0.md`
- Release runbook: `docs/RELEASE_RUNBOOK_v0.2.0.md`
