# Roadmap

This document tracks the recommended next development focus for `spring-privacy-guard` after the current feature set around masking, auditing, dead letters, alert delivery, receiver verification, and replay protection.

## 0.2.0 Focus

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

## Near-Term TODO (Priority)

- No additional `0.2.0` P2 tasks remain. Next work should be selected from the candidate `0.3.0` themes below.

## Post-0.3.0 Themes

- Expand the new multi-tenant policy surface beyond masking/text rules and tenant-scoped management helpers
- Add repository-native tenant isolation for audit and dead-letter query/persistence paths
- Additional observability integrations
- Broader sample applications and deployment recipes
