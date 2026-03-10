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

## Candidate 0.3.0 Themes

- Multi-tenant privacy policies
- Pluggable persistence for receiver replay stores
- Additional observability integrations
- Broader sample applications and deployment recipes
