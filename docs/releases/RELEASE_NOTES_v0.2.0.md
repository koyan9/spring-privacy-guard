# spring-privacy-guard v0.2.0

## Highlights

- Stable custom masking SPI with `@StableSpi` markers and configurable text-pattern masking rules
- Async and batched audit publishing, dead-letter replay/export/import, and configurable executor concurrency
- JDBC-backed audit, dead-letter, and receiver replay-store support with production rollout guidance
- Built-in webhook and email alert callbacks plus signed receiver verification with filter and interceptor modes
- Actuator and Micrometer observability for backlog state, webhook failures, replay-store cleanup, and verification reason codes

## Included Capabilities

### Core

- `MaskingService`
- `MaskingStrategy`
- `MaskingContext`
- `TextMaskingService`
- `TextMaskingRule`
- `StableSpi`

### Audit & Dead Letters

- `PrivacyAuditService`
- `PrivacyAuditQueryService`
- `PrivacyAuditStatsService`
- `PrivacyAuditDeadLetterService`
- `PrivacyAuditDeadLetterRepository`
- `AsyncPrivacyAuditPublisher`
- `BufferedPrivacyAuditPublisher`
- `PrivacyAuditDeadLetterExchangeService`

### Observability & Alerts

- `PrivacyAuditDeadLetterHealthIndicator`
- `PrivacyAuditDeadLetterMetricsBinder`
- `PrivacyAuditDeadLetterWebhookAlertTelemetry`
- `PrivacyAuditDeadLetterWebhookVerificationTelemetry`
- `LoggingPrivacyAuditDeadLetterAlertCallback`
- `PrivacyAuditDeadLetterWebhookAlertCallback`
- `PrivacyAuditDeadLetterEmailAlertCallback`

### Receiver Verification

- `PrivacyAuditDeadLetterWebhookRequestVerifier`
- `PrivacyAuditDeadLetterWebhookReplayStore`
- `InMemoryPrivacyAuditDeadLetterWebhookReplayStore`
- `FilePrivacyAuditDeadLetterWebhookReplayStore`
- `JdbcPrivacyAuditDeadLetterWebhookReplayStore`
- `PrivacyAuditDeadLetterWebhookVerificationFilter`
- `PrivacyAuditDeadLetterWebhookVerificationInterceptor`

## Upgrade Notes

- No intentional breaking changes are included in `v0.2.0`
- Stable extension points are marked with `@StableSpi`; built-in runtime wiring classes remain internal implementation details
- Use `docs/JDBC_PRODUCTION_GUIDE.md` for MySQL/PostgreSQL rollout, schema management, and migration planning
- If you compile or run `samples/privacy-demo/` from a fresh clone, install local starter artifacts first with `./mvnw -q -DskipTests install` or `mvnw.cmd -q -DskipTests install`

## Verification

- `mvnw.cmd -q test -pl privacy-guard-core,privacy-guard-spring-boot-starter` or `./mvnw -q test -pl privacy-guard-core,privacy-guard-spring-boot-starter`
- `mvnw.cmd -q verify` or `./mvnw -q verify`
- `python scripts/check_repo_hygiene.py`

## Release Checklist

- Drafted from the `0.2.0` section in `CHANGELOG.md`
- Reviewed against `docs/RELEASE_CHECKLIST.md`
- Intended for tag `v0.2.0`
