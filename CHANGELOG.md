# Changelog

All notable changes to `spring-privacy-guard` will be documented in this file.

## [Unreleased]

- Added a runnable `jdbc-tenant` sample profile with H2-backed JDBC audit, dead-letter, tenant-column, and replay-store wiring.
- Added a sample integration test and deployment recipe docs for the local JDBC tenant profile.
- Added Micrometer counters for tenant-aware query path selection (`native` vs `fallback`) across audit and dead-letter helper reads.

## [0.3.0] - TBD

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
- Expanded release-readiness and maintainer documentation for the next `v0.3.0` cut, including draft release notes, operator guides, and release copy.

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
