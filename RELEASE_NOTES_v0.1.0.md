# spring-privacy-guard v0.1.0

## Highlights

- First public release of `spring-privacy-guard`
- Annotation-based masking for Spring Boot JSON responses
- Free-text log sanitization and Logback integration
- Audit publishing, persistence, querying, and stats

## Included Capabilities

- `@SensitiveData` field masking
- Jackson output masking
- `PrivacyLoggerFactory`
- `PrivacyPatternLayout`
- `PrivacySanitizingAppender`
- `PrivacyBlockingTurboFilter`
- `PrivacyAuditService`
- `PrivacyAuditQueryService`
- `PrivacyAuditStatsService`

## Persistence

- `IN_MEMORY`
- `JDBC`
- Built-in SQL scripts for generic / H2 / PostgreSQL / MySQL
- Optional schema initialization

## Verification

- `./mvnw -q verify`