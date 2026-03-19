# GitHub Release Copy: v0.2.0

Use the content below directly in the GitHub Release editor if you do not want to paste `docs/releases/RELEASE_NOTES_v0.2.0.md` manually.

---

spring-privacy-guard v0.2.0

Highlights

- Stable custom masking SPI with `@StableSpi` markers and configurable text-pattern masking rules
- Async and batched audit publishing, dead-letter replay/export/import, and configurable executor concurrency
- JDBC-backed audit, dead-letter, and receiver replay-store support with production rollout guidance
- Built-in webhook and email alert callbacks plus signed receiver verification with filter and interceptor modes
- Actuator and Micrometer observability for backlog state, webhook failures, replay-store cleanup, and verification reason codes

Included Capabilities

Core

- MaskingService
- MaskingStrategy
- MaskingContext
- TextMaskingService
- TextMaskingRule
- StableSpi

Audit and Dead Letters

- PrivacyAuditService
- PrivacyAuditQueryService
- PrivacyAuditStatsService
- PrivacyAuditDeadLetterService
- PrivacyAuditDeadLetterRepository
- AsyncPrivacyAuditPublisher
- BufferedPrivacyAuditPublisher
- PrivacyAuditDeadLetterExchangeService

Observability and Alerts

- PrivacyAuditDeadLetterHealthIndicator
- PrivacyAuditDeadLetterMetricsBinder
- PrivacyAuditDeadLetterWebhookAlertTelemetry
- PrivacyAuditDeadLetterWebhookVerificationTelemetry
- LoggingPrivacyAuditDeadLetterAlertCallback
- PrivacyAuditDeadLetterWebhookAlertCallback
- PrivacyAuditDeadLetterEmailAlertCallback

Receiver Verification

- PrivacyAuditDeadLetterWebhookRequestVerifier
- PrivacyAuditDeadLetterWebhookReplayStore
- InMemoryPrivacyAuditDeadLetterWebhookReplayStore
- FilePrivacyAuditDeadLetterWebhookReplayStore
- JdbcPrivacyAuditDeadLetterWebhookReplayStore
- PrivacyAuditDeadLetterWebhookVerificationFilter
- PrivacyAuditDeadLetterWebhookVerificationInterceptor

Upgrade Notes

- No intentional breaking changes are included in v0.2.0
- Stable extension points are marked with `@StableSpi`; built-in runtime wiring classes remain internal implementation details
- Use `docs/JDBC_PRODUCTION_GUIDE.md` for MySQL/PostgreSQL rollout, schema management, and migration planning
- If you compile or run `samples/privacy-demo/` from a fresh clone, install local starter artifacts first with `./mvnw -q -DskipTests install` or `mvnw.cmd -q -DskipTests install`

Verification

- `mvnw.cmd -q test -pl privacy-guard-core,privacy-guard-spring-boot-starter` or `./mvnw -q test -pl privacy-guard-core,privacy-guard-spring-boot-starter`
- `mvnw.cmd -q verify` or `./mvnw -q verify`
- `python scripts/check_repo_hygiene.py`

---

Suggested release title:

`spring-privacy-guard v0.2.0`
