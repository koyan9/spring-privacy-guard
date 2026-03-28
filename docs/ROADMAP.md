# Roadmap

This document tracks the recommended next development focus for `spring-privacy-guard` after the published `v0.5.0` release on 2026-03-28.

## Historical Focus

1. Receiver operations polish
   - Add replay-store health/dashboard guidance
   - Add receiver storage backends beyond local files (JDBC replay store)
   - Add clearer receiver deployment examples
2. Alert delivery hardening
   - Optional jitter/backoff policies for webhook delivery
   - More structured email/webhook payload customization
   - Better operational failure diagnostics
3. Repository expansion
   - More JDBC examples and production database guides
   - Migration guidance for table evolution
4. API stabilization
   - Review alert/receiver property names
   - Mark stable SPI extension points in docs
5. Release readiness
   - Tighten docs consistency
   - Improve badges/topics/about metadata
   - Prepare release note content and verification steps

## Recently Completed

1. Receiver verification failure metrics with reason tags (`privacy.audit.deadletters.receiver.verification.failures{reason=...}`) + tests
2. Webhook alert retry backoff policy (`FIXED`/`EXPONENTIAL`) with optional jitter/max-backoff and failure category metrics
3. Replay-store cleanup diagnostics metrics (last cleanup count/duration/timestamp)
4. Dead-letter export/import scalability (paging/streaming)
5. Configurable audit executor concurrency
6. MDC / structured logging masking support
7. Configurable regex or custom sensitive type patterns
8. JDBC production guide and migration notes for audit, dead-letter, and replay-store tables
9. Stable SPI markers across supported extension points and their carrier types
10. Release-readiness docs consistency, verification notes, and GitHub release copy alignment
11. Multi-tenant integration guide covering request-header contract, stable SPI, and sample management flows
12. Redis-backed receiver replay store with auto-configuration and receiver operations guidance
13. Baseline tenant-aware masking, audit-detail policy resolution, and tenant-scoped management helpers
14. Tenant-path Micrometer telemetry, sample observability endpoint, and post-release docs alignment for `v0.3.0`
15. Tenant-native dead-letter replay for built-in `IN_MEMORY` / `JDBC` repositories
16. Stable tenant logging policy resolution over the existing tenant property model
17. Cross-platform JDBC / Redis multi-instance verification scripts for the sample app
18. Tenant-scoped dead-letter observability policy overrides for warning/down thresholds and recovery notifications
19. Tenant operational telemetry for alert transitions, delivery outcomes, and receiver route verification failures
20. PostgreSQL + Redis production-like sample profiles, compose recipe, and verification scripts
21. Explicit tenant-aware single dead-letter delete and replay helpers for `id`-based management flows, plus sample HTTP coverage
22. Stable tenant dead-letter alert route policy resolution with bridge support for the legacy tenant route map
23. Stable tenant dead-letter alert delivery policy resolution for logging / webhook / email channel toggles
24. Stable tenant dead-letter alert monitoring policy resolution for per-tenant alert-monitor membership control
25. Explicit tenant-native dead-letter delete-by-id / replay-by-id capability flags and write-path telemetry
26. Draft `v0.5.0` release notes, execution guide, runbook, GitHub release copy, dry-run record, and announcement set

## Recommended Next Work

- Finalize the `v0.5.0` release candidate validation, published version references, and tag-ready release checklist
- Add more production-oriented samples and rollout guides beyond the current PostgreSQL + Redis reference
- Explore the next multi-tenant policy surface beyond masking/text, audit-detail filtering, dead-letter alerting, and current logging selection
- Explore the next repository-native tenant-isolation expansion beyond the current built-in read/write/delete/replay/import and explicit by-id support

## Post-v0.4.0 Themes

- Expand the new multi-tenant policy surface beyond masking/text rules and tenant-scoped management helpers
- Add repository-native tenant isolation for audit and dead-letter query/persistence paths beyond the current built-in read/write/delete/replay and explicit by-id management support
- Additional observability integrations
- Broader sample applications and deployment recipes
