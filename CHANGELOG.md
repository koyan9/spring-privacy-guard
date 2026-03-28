# Changelog

All notable changes to `spring-privacy-guard` will be documented in this file.

## [0.5.0] - 2026-03-28

### Added

- Added stable tenant dead-letter alert route policy carriers and a bridge resolver so per-tenant webhook, email, and receiver route overrides can move under `privacy.guard.tenant.policies.*.observability.dead-letter.alert.*` while remaining backward-compatible with the existing `alert.tenant.routes.*` configuration.
- Added stable tenant dead-letter alert delivery policy resolution so per-tenant logging, webhook, and email delivery can be enabled or disabled independently of the global dead-letter alert defaults.
- Added stable tenant dead-letter alert monitoring policy resolution so per-tenant alert monitor membership can be enabled or disabled independently of the global monitored-tenant allowlist.
- Added explicit tenant-aware single dead-letter delete and replay helpers for `id`-based management flows while preserving the existing global by-id entry points for opt-in global administration.
- Added tenant-native dead-letter lookup by `id` for built-in repositories and sample custom repositories so tenant-aware single-entry management no longer has to rely on global lookup plus helper-side ownership filtering.
- Added explicit tenant-native dead-letter delete-by-id and replay-by-id capability flags plus dedicated `dead_letter_delete_by_id` / `dead_letter_replay_by_id` write-path telemetry.
- Added `dead_letter_find_by_id` tenant read-path telemetry plus sample observability coverage so single-entry dead-letter ownership lookup now reports native vs fallback execution separately from criteria reads and exchange paths.
- Replaced reflective detection of tenant-native dead-letter by-id lookup with an explicit SPI capability so helper routing and sample capability reporting now use the same declared contract.
- Added explicit SPI capability flags for tenant-native dead-letter exchange reads and tenant-aware import writes so helper routing and sample capability reporting no longer infer those paths from interface presence alone.
- Added explicit SPI capability flags for audit tenant reads and writes so query helpers, publishers, and sample capability reporting no longer infer native support from interface presence alone.
- Added explicit SPI capability flags for dead-letter generic reads, writes, deletes, and replays so helper routing and sample capability reporting no longer infer native support from interface presence alone.
- Added sample HTTP coverage for tenant-scoped single dead-letter delete and replay operations through both the default in-memory profile and the JDBC tenant profile.

### Changed

- Updated `.github/workflows/release.yml` to use `softprops/action-gh-release@v2.6.1` so future release runs avoid the Node 20 crash observed during the `v0.4.0` publication workflow.

## [0.4.0] - 2026-03-27

### Added

- Added `PrivacyTenantDeadLetterObservabilityPolicy` and resolver support so tenant policies can override dead-letter warning/down thresholds and recovery notifications without changing existing stable audit or logging policy carriers.
- Added tenant alert transition and delivery Micrometer telemetry plus receiver route failure counters for tenant-specific verification paths.
- Added tenant-aware dead-letter import persistence so tenant-scoped import prefers repository-native bulk write hints and records `dead_letter_import` native/fallback telemetry.
- Added tenant-scoped dead-letter export and manifest path telemetry plus sample repository capability reporting for tenant exchange read/write support.
- Added a `fallback-tenant` sample profile that registers generic-only repositories so tenant native/fallback behavior can be compared locally.
- Added a `custom-tenant-native` sample profile with custom tenant-aware repositories that implement the tenant SPI without relying on the built-in repository implementations.
- Added a `custom-jdbc-tenant` sample profile with sample-defined JDBC tenant repositories, schema initialization, and native tenant-path verification.
- Added a `custom-jdbc-tenant-node2` overlay plus multi-instance verification scripts so the custom JDBC tenant repositories can also be rehearsed in a two-node local rollout.
- Added a PostgreSQL + Redis production-like sample profile pair, local compose recipe, and verification scripts for two-node rollout rehearsal.
- Added a runnable `jdbc-tenant` sample profile with H2-backed JDBC audit, dead-letter, tenant-column, and replay-store wiring.
- Added a sample integration test and deployment recipe docs for the local JDBC tenant profile.
- Added Micrometer counters for tenant-aware query path selection (`native` vs `fallback`) across audit and dead-letter helper reads.
- Added a protected `/demo-tenants/observability` sample endpoint plus integration coverage for tenant-path metrics.
- Extended `PrivacyTenantContextSnapshot` so async callback chains can wrap `Executor`, `Consumer`, `Function`, and `Bi*` functional interfaces in addition to `Runnable`/`Callable`/`Supplier`.
- Added tenant-scoped dead-letter backlog gauges and tenant-aware dead-letter alert callback routing, including built-in webhook/email wrappers that carry `tenantId` in remote notifications.
- Added explicit tenant-to-target route overrides for built-in dead-letter webhook/email alerts.
- Added tenant-specific receiver verification routes so built-in filter/interceptor mode can select bearer/signature settings by path.
- Added `PrivacyTenantAuditDeadLetterDeleteRepository` so tenant-scoped dead-letter deletes can prefer repository-native criteria deletion.
- Added `PrivacyTenantAuditDeadLetterReplayRepository` so tenant-scoped dead-letter replay can prefer repository-native criteria replay.
- Added `PrivacyTenantLoggingPolicy` and `PrivacyTenantLoggingPolicyResolver` as stable tenant logging policy surfaces over the existing property model.
- Added a tenant-aware dead-letter Actuator health indicator that summarizes backlog state across the configured tenant list.
- Added tenant write-path telemetry for dead-letter criteria delete and replay management flows.
- Upgraded the `jdbc-tenant` sample into a shared-JDBC local two-instance rollout reference with a node-2 overlay profile, instance metadata, and a multi-instance verification script.
- Added Redis-backed sample profiles and a local multi-instance verification script for shared replay-store receiver deployments that already use Redis.
- Added a Unix shell helper for bringing the sample's local Redis dependency up and down through the bundled Docker Compose file.

### Changed

- Improved tenant-scoped dead-letter replay to reuse preselected entries instead of reloading each selected id before publish/delete.

### Fixed

- Fixed tenant observability auto-configuration to bind Micrometer lazily so Boot metrics initialization order does not suppress counters.

## [0.3.0] - 2026-03-20

### Added

- Redis-backed webhook replay store support for shared receiver nonce protection without JDBC schema management.
- Redis replay-store auto-configuration, property metadata, and receiver operations guidance.
- Core tenant-resolution SPI with `PrivacyTenantProvider`, `PrivacyTenantPolicyResolver`, `PrivacyTenantPolicy`, and request-header tenant propagation.
- Non-web tenant-context propagation utilities with `PrivacyTenantContextScope` and `PrivacyTenantContextSnapshot`.
- `PrivacyTenantAwareMaskingStrategy`, tenant policy configuration binding, and sample multi-tenant masking examples.
- Tenant-specific audit detail filtering and tenant-tagging rules through `PrivacyTenantAuditPolicy` and `PrivacyTenantAuditPolicyResolver`.
- `PrivacyTenantAuditQueryService` as a non-breaking helper for tenant-filtered audit reads and stats.
- `PrivacyTenantAuditReadRepository` and `PrivacyTenantAuditDeadLetterReadRepository` as tenant-native read SPIs for repositories that can pre-filter by tenant detail tags.
- `PrivacyTenantAuditWriteRepository` and `PrivacyTenantAuditDeadLetterWriteRepository` as tenant-aware persistence SPIs for repositories that want explicit write hints without changing stable event contracts.
- Optional JDBC tenant-column configuration for built-in audit and dead-letter repositories.
- `PrivacyTenantAuditDeadLetterQueryService` for tenant-filtered dead-letter reads and stats.
- `PrivacyTenantAuditDeadLetterOperationsService` for tenant-filtered dead-letter batch delete and replay operations.
- `PrivacyTenantAuditDeadLetterExchangeService` for tenant-filtered dead-letter export, import, and manifest flows.
- `PrivacyTenantAuditManagementService` as a unified tenant-aware facade over audit and dead-letter management helpers.
- Sample tenant-management endpoints and documentation for current-tenant inspection, tenant policy visibility, and tenant-scoped audit/dead-letter management.
- Dedicated multi-tenant integration guide covering request/header contracts, stable SPI surfaces, and sample management flows.

### Changed

- Updated `samples/privacy-demo` to route its tenant-aware audit and dead-letter management flows through the starter-provided tenant facade and helpers.
- Built-in in-memory and JDBC repositories now provide tenant-native read paths that tenant helpers can prefer before falling back to cross-page filtering.
- Built-in repository publisher, async publisher, buffered publisher, and repository-backed dead-letter handler now preserve tenant-aware write hints across synchronous and asynchronous persistence flows.
- Built-in JDBC schema resources and property metadata now cover optional dedicated tenant columns for audit and dead-letter tables.
- Expanded release-readiness and maintainer documentation for the `v0.3.0` release, including release notes, operator guides, and GitHub release copy.

### Fixed

- Receiver auto-configuration now isolates optional JDBC and Redis replay-store support so applications without those dependencies do not fail at startup.

## [0.2.0] - 2026-03-09

### Added

- Contributor quick-start guide in `AGENTS.md`
- Additional Spring configuration metadata for `privacy.guard.*` properties and enum hints
- JaCoCo coverage reports and an 80% line-coverage gate for published modules
- Web-layer integration coverage for MVC JSON responses in the starter module
- Custom `MaskingStrategy` SPI support in the core module and Spring Boot starter wiring
- `@StableSpi` markers for supported extension points and directly coupled carrier types in the core module and starter
- Runnable sample `MaskingStrategy` bean demonstrating custom name masking in `privacy-demo`
- Sample integration test covering the custom name masking response
- Optional asynchronous audit publishing with a dedicated executor, retry policy, and configurable thread name prefix
- Configurable audit executor concurrency for async and batched publishing
- Pluggable dead-letter repositories with built-in `IN_MEMORY` and `JDBC` persistence options for exhausted retries
- Dead-letter replay service, repository query/delete support, and sample HTTP endpoints
- Optional buffered and batched audit persistence with repository bulk writes, retries, dead-letter handling, and scheduled flushing
- Dead-letter filtering, pagination, cleanup, stats, and paged/streaming JSON/CSV export/import APIs with checksum manifests, import deduplication, and operation audit tracing in the sample app
- Sample-only admin token protection for dead-letter management endpoints, including denied-access audit events
- Optional Actuator dead-letter health contributor, tagged Micrometer backlog gauges, default logging alerts, and threshold alert callback SPI support
- Receiver verification failure metrics with reason tags, replay-store cleanup diagnostics metrics, and webhook failure-category telemetry
- Sample signed webhook receiver example for verifying incoming dead-letter alerts
- Reusable starter webhook and email alert callback implementations for dead-letter thresholds
- Reusable starter webhook verifier and replay-store SPI for signed alert receivers
- JDBC-backed webhook replay store for multi-instance receiver deployments, with schema resources and optional auto-initialization
- JDBC production guide and migration notes across audit, dead-letter, and replay-store tables
- Starter replay-store metrics and sample replay-store stats management endpoints
- Spring MVC receiver verification filter and interceptor support
- Property-driven receiver verification settings, file-backed replay-store configuration, and JSON reason codes for verification failures
- Chinese project overview in `README.zh-CN.md`

### Changed

- Expanded and reorganized `README.md`, `README.zh-CN.md`, and `samples/privacy-demo/README.md` with clearer quick-start, configuration, receiver, observability, sample mode-switch, and release-readiness guidance
- Documented demo.admin.* sample settings, token-protected dead-letter management usage, starter webhook/email alert properties, receiver verification settings, tagged dead-letter metrics, replay-store metrics, stable SPI boundaries, and JDBC rollout guidance
- Release automation now resolves `docs/releases/RELEASE_NOTES_<tag>.md` dynamically and falls back to `docs/releases/RELEASE_NOTES_TEMPLATE.md`
- Moved release notes under `docs/releases/` and updated release tooling references
- Increased core masking test coverage with null, blank, short-value, multi-token, multi-match, and custom-strategy scenarios
- Expanded starter auto-configuration integration tests for Boot ObjectMapper masking, JDBC schema initialization, custom strategy injection, async audit publishing, dead-letter repositories, receiver filter/interceptor wiring, and replay-store metrics
- Logging sanitization now covers MDC and structured logging fields, and text masking now supports configurable built-in patterns plus additional `SensitiveType`-mapped rules
- Webhook alert retry backoff now supports fixed or exponential policies with optional jitter and max-backoff controls
- CI now uploads JaCoCo reports for the Java 17 verification job

### Fixed

- Clarified that the sample requires locally installed starter artifacts before standalone compilation
- Prevented the starter from failing to load when `spring-jdbc` is absent and JDBC auditing is unused
- Improved replay-store snapshot accuracy by excluding expired entries

## [0.1.0] - 2026-03-07

### Added

- Initial field-level masking support
- Jackson JSON masking integration
- Free-text log sanitization
- Logback layout, appender, and turbo-filter support
- Audit publishing, persistence, querying, pagination, sorting, fuzzy matching, and stats
- Built-in `IN_MEMORY` and `JDBC` audit repositories
- Multi-database schema selection and optional initializer
- Privacy demo sample project

### Changed

- Audit querying evolved from simple recent-event retrieval to a shared query and stats abstraction

### Fixed

- Improved consistency between in-memory and JDBC-backed audit query behavior
