# Changelog

All notable changes to `spring-privacy-guard` will be documented in this file.

## [Unreleased]

- Added a JDBC-backed webhook replay store for multi-instance receiver deployments.
- Added replay store JDBC schema resources and auto-configuration for optional schema initialization.
- Moved release notes under `docs/releases/` and updated release tooling references.
- Added property-driven receiver verification settings and file-backed replay-store configuration.
- Added receiver verification reason codes in HTTP error responses.
- Improved replay-store snapshot accuracy by excluding expired entries.
- Improved webhook alert delivery diagnostics with structured failure details.
- Added webhook alert failure metrics for status, retryability, and failure types.

## [0.2.0] - 2026-03-09

### Added

- Contributor quick-start guide in `AGENTS.md`
- Additional Spring configuration metadata for `privacy.guard.*` properties and enum hints
- JaCoCo coverage reports and an 80% line-coverage gate for published modules
- Web-layer integration coverage for MVC JSON responses in the starter module
- Custom `MaskingStrategy` SPI support in the core module and Spring Boot starter wiring
- Runnable sample `MaskingStrategy` bean demonstrating custom name masking in `privacy-demo`
- Sample integration test covering the custom name masking response
- Optional asynchronous audit publishing with a dedicated executor, retry policy, and configurable thread name prefix
- Pluggable dead-letter repositories with built-in `IN_MEMORY` and `JDBC` persistence options for exhausted retries
- Dead-letter replay service, repository query/delete support, and sample HTTP endpoints
- Optional buffered and batched audit persistence with repository bulk writes, retries, dead-letter handling, and scheduled flushing
- Dead-letter filtering, pagination, cleanup, stats, and JSON/CSV export/import APIs with checksum manifests, import deduplication, and operation audit tracing in the sample app
- Sample-only admin token protection for dead-letter management endpoints, including denied-access audit events
- Optional Actuator dead-letter health contributor, tagged Micrometer backlog gauges, default logging alerts, and threshold alert callback SPI support
- Sample signed webhook receiver example for verifying incoming dead-letter alerts
- Reusable starter webhook and email alert callback implementations for dead-letter thresholds
- Reusable starter webhook verifier and replay-store SPI for signed alert receivers
- Starter replay-store metrics and sample replay-store stats management endpoints
- Spring MVC receiver verification filter and interceptor support
- Chinese project overview in `README.zh-CN.md`

### Changed

- Expanded and reorganized `README.md`, `README.zh-CN.md`, and `samples/privacy-demo/README.md` with clearer quick-start, configuration, receiver, observability, sample mode-switch, and release-readiness guidance
- Documented demo.admin.* sample settings, token-protected dead-letter management usage, starter webhook/email alert properties, receiver verification settings, tagged dead-letter metrics, and replay-store metrics
- Release automation now resolves `docs/releases/RELEASE_NOTES_<tag>.md` dynamically and falls back to `docs/releases/RELEASE_NOTES_TEMPLATE.md`
- Increased core masking test coverage with null, blank, short-value, multi-token, multi-match, and custom-strategy scenarios
- Expanded starter auto-configuration integration tests for Boot ObjectMapper masking, JDBC schema initialization, custom strategy injection, async audit publishing, dead-letter repositories, receiver filter/interceptor wiring, and replay-store metrics
- CI now uploads JaCoCo reports for the Java 17 verification job

### Fixed

- Clarified that the sample requires locally installed starter artifacts before standalone compilation
- Prevented the starter from failing to load when `spring-jdbc` is absent and JDBC auditing is unused

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
