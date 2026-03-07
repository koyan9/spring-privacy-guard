# Changelog

All notable changes to `spring-privacy-guard` will be documented in this file.

## [Unreleased]

### Added

- 

### Changed

- 

### Fixed

- 

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