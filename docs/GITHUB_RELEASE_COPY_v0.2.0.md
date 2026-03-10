# GitHub Release Copy: v0.2.0

Use the content below directly in the GitHub Release editor if you do not want to paste `docs/releases/RELEASE_NOTES_v0.2.0.md` manually.

---

spring-privacy-guard v0.2.0

Highlights

- Custom masking SPI and stronger masking test coverage
- Async and batched audit publishing with retry and dead-letter fallback
- Dead-letter query, cleanup, replay, export/import, checksum validation, and operation audit trails
- Actuator and Micrometer observability for dead-letter backlog, webhook delivery, receiver replay-store state, and signed alert verification flows
- Built-in webhook and email alert callbacks plus signed receiver verification with filter and interceptor modes

Included Capabilities

Core

- MaskingService
- MaskingStrategy
- MaskingContext
- TextMaskingService

Audit and Dead Letters

- PrivacyAuditService
- PrivacyAuditQueryService
- PrivacyAuditStatsService
- PrivacyAuditDeadLetterService
- AsyncPrivacyAuditPublisher
- BufferedPrivacyAuditPublisher
- PrivacyAuditDeadLetterExchangeService

Observability and Alerts

- PrivacyAuditDeadLetterHealthIndicator
- PrivacyAuditDeadLetterMetricsBinder
- LoggingPrivacyAuditDeadLetterAlertCallback
- PrivacyAuditDeadLetterWebhookAlertCallback
- PrivacyAuditDeadLetterEmailAlertCallback
- Replay-store metrics for receiver nonce state

Receiver Verification

- PrivacyAuditDeadLetterWebhookRequestVerifier
- PrivacyAuditDeadLetterWebhookReplayStore
- InMemoryPrivacyAuditDeadLetterWebhookReplayStore
- FilePrivacyAuditDeadLetterWebhookReplayStore
- PrivacyAuditDeadLetterWebhookVerificationFilter
- PrivacyAuditDeadLetterWebhookVerificationInterceptor

Upgrade Notes

- No intentional breaking changes are included in v0.2.0
- If you compile or run `samples/privacy-demo/` from a fresh clone, install local starter artifacts first with `./mvnw -q -DskipTests install` or `mvnw.cmd -q -DskipTests install`
- The project version has already been aligned to `0.2.0` across publishable modules and sample configuration

Verification

- `./mvnw -q verify`
- `./mvnw -q -f samples/privacy-demo/pom.xml test`

---

Suggested release title:

`spring-privacy-guard v0.2.0`
