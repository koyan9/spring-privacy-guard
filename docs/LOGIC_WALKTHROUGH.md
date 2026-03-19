# Logic Walkthrough

This document summarizes the runtime logic in the `privacy-guard-core` and `privacy-guard-spring-boot-starter` modules as of the current main branch. It is intended for developers and architects who need to understand control flow, key responsibilities, and integration points.

## Scope

- Modules covered: `privacy-guard-core`, `privacy-guard-spring-boot-starter`
- Focus: masking, logging sanitization, audit publishing, dead-letter workflows, alerts, receiver verification, and Spring Boot auto-configuration
- This is a behavior walkthrough, not an API reference or a deployment guide

## Layered Architecture

1. Core masking (privacy-guard-core)
The core module provides masking rules and a strategy SPI. The main entry is `MaskingService`, which builds a `MaskingContext` from `SensitiveData` or `SensitiveType`, resolves the current tenant policy when configured, tries custom strategies first, and then falls back to built-in rules. `TextMaskingService` applies regex-based detection for email, phone, and ID patterns (configurable via `privacy.guard.masking.text.*` and tenant overrides), delegating to `MaskingService` for the actual masking logic.

2. Serialization and logging integration (starter)
The starter integrates core masking with Jackson serialization and application logging. `PrivacyGuardModule` registers a `PrivacyGuardBeanSerializerModifier` which wraps string fields annotated with `@SensitiveData` using `MaskingBeanPropertyWriter`. Logging is handled via `PrivacyLogSanitizer` and `PrivacyLogger`, with optional Logback integration (`PrivacyPatternLayout`, `PrivacySanitizingAppender`, `PrivacyBlockingTurboFilter`) to sanitize formatted output, MDC values, structured key/value fields, or block unsafe messages.

3. Audit pipeline (starter)
The audit pipeline centers on `PrivacyAuditService.record(...)`, which sanitizes input values, optionally applies tenant-specific audit detail policy, and publishes a `PrivacyAuditEvent` through `PrivacyAuditPublisher`. The publisher is typically a `CompositePrivacyAuditPublisher` combining an application event publisher and one or more repository publishers. The pipeline can be wrapped with `AsyncPrivacyAuditPublisher` or `BufferedPrivacyAuditPublisher` depending on configuration, and failures route into a dead-letter handler. Tenant-scoped read paths can reuse `PrivacyTenantAuditQueryService`, which filters the existing query results by the configured tenant detail key without changing `PrivacyAuditEvent` or `PrivacyAuditQueryCriteria`.

4. Dead-letter management and exchange (starter)
Dead-letter persistence is abstracted by `PrivacyAuditDeadLetterRepository` with in-memory or JDBC implementations. `PrivacyAuditDeadLetterService` provides replay, delete, and criteria-based queries. `PrivacyTenantAuditDeadLetterQueryService` adds tenant-filtered read and stats helpers based on the configured tenant detail key, `PrivacyTenantAuditDeadLetterOperationsService` applies the same tenant scoping to batch delete and replay flows, and `PrivacyTenantAuditDeadLetterExchangeService` scopes export/import/manifest operations to a tenant while preserving the uploaded checksum contract. `PrivacyTenantAuditManagementService` sits above these helpers as a unified orchestration layer for tenant-scoped audit and dead-letter management flows. `PrivacyAuditDeadLetterExchangeService` remains the tenant-agnostic primitive for JSON/CSV export, import, checksum verification, and optional deduplication using content fingerprints.

5. Observability and alerting (starter)
Dead-letter observability is built on `PrivacyAuditDeadLetterStatsService`, which feeds `PrivacyAuditDeadLetterObservationService` to produce a backlog snapshot and state. `PrivacyAuditDeadLetterAlertMonitor` schedules periodic checks and invokes alert callbacks (logging, webhook, email). Optional Micrometer binders expose gauge and counter metrics for backlogs and webhook delivery telemetry.

6. Receiver verification and replay protection (starter)
Incoming webhook verification is handled by `PrivacyAuditDeadLetterWebhookRequestVerifier`. It validates authorization (optional), signature headers, timestamp skew, and replay detection. Replay protection is provided by `PrivacyAuditDeadLetterWebhookReplayStore` with in-memory, file-based, Redis-backed, or JDBC-backed implementations. Servlet integration is provided by a filter or interceptor, with a body-caching filter to allow downstream handlers to read the request body when using interceptors.

7. Auto-configuration (starter)
Spring Boot auto-configuration wires the above pieces based on `privacy.guard.*` properties and classpath conditions. It creates default beans for masking, tenant policy resolution, logging, audit repositories, publishers, dead-letter handlers, metrics, and receiver verification components. JDBC repositories and schema initialization are conditional on `JdbcOperations` and explicit property flags.

## Key Data Models

- `SensitiveData` and `SensitiveType` describe masking semantics at the field level.
- `PrivacyTenantPolicy` describes tenant-specific fallback mask characters and text masking rules.
- `PrivacyTenantAuditPolicy` describes tenant-specific audit detail filtering and tenant-tagging rules.
- `PrivacyAuditEvent` captures audit context and is the unit of publishing.
- `PrivacyAuditDeadLetterEntry` represents failed audit persistence attempts, including error metadata.
- `PrivacyAuditQueryCriteria` and `PrivacyAuditDeadLetterQueryCriteria` normalize filters, defaulting to `DESC` sort order and a limit of 100 when not specified.
- `PrivacyAuditDeadLetterBacklogSnapshot` and `PrivacyAuditDeadLetterWebhookReplayStoreSnapshot` capture observability state for alerts and metrics.

## Key Flows (Text Diagrams)

### Masking via Jackson

```
Model field with @SensitiveData
  -> PrivacyGuardModule
    -> PrivacyGuardBeanSerializerModifier
      -> MaskingBeanPropertyWriter
        -> MaskingService.mask(value, SensitiveData)
          -> custom MaskingStrategy (if supports)
          -> built-in masking (NAME/PHONE/EMAIL/ID_CARD/ADDRESS/GENERIC)
```

### Text log sanitization

```
App logging call
  -> PrivacyLogger
    -> PrivacyLogSanitizer
      -> TextMaskingService (regex match)
        -> MaskingService.mask(value, SensitiveType)
```

### Logback blocking (optional)

```
Logback TurboFilter
  -> PrivacyBlockingTurboFilter
    -> PrivacyLogbackRuntime.containsSensitiveData
      -> PrivacyLogSanitizer.containsSensitiveData
        -> TextMaskingService + MaskingService
```

### Audit publish path

```
PrivacyAuditService.record(...)
  -> PrivacyAuditPublisher (Composite)
    -> ApplicationEventPrivacyAuditPublisher
    -> Repository publisher
       -> AsyncPrivacyAuditPublisher (optional)
       -> BufferedPrivacyAuditPublisher (optional)
          -> PrivacyAuditRepository.save/saveAll
          -> Dead-letter handler on failure
```

### Dead-letter replay

```
PrivacyAuditDeadLetterService.replay(id)
  -> PrivacyAuditDeadLetterRepository.findById
  -> privacyAuditReplayPublisher.publish(event)
  -> deleteById on success
```

### Dead-letter export/import

```
PrivacyAuditDeadLetterExchangeService
  -> exportJson/exportCsv (paged writer overloads)
  -> exportManifest (streamed sha256)
  -> importJson/importCsv (streaming + batched saveAll)
     -> checksum verify
     -> optional dedup (fingerprint)
     -> saveAll
```

### Dead-letter alerting

```
PrivacyAuditDeadLetterStatsService
  -> PrivacyAuditDeadLetterObservationService.currentSnapshot
  -> PrivacyAuditDeadLetterAlertMonitor (scheduled)
    -> Alert callbacks (logging/webhook/email)
    -> Webhook telemetry (Micrometer) if enabled
```

### Receiver verification

```
Filter or Interceptor
  -> PrivacyAuditDeadLetterWebhookRequestVerifier.verify
     -> optional bearer token
     -> signature header + timestamp skew
     -> replay store markIfNew(nonce)
  -> allow request or return 4xx
```

## Failure and Retry Behavior

- `AsyncPrivacyAuditPublisher` retries publish attempts with a configurable backoff. After max attempts, it routes to `PrivacyAuditDeadLetterHandler`.
- `BufferedPrivacyAuditPublisher` batches events, flushes on schedule or size, retries batch persistence, and sends each event to dead-letter handling if the batch ultimately fails.
- Webhook alert callbacks retry delivery according to configured max attempts and backoff, recording telemetry when enabled.

## Threading and Scheduling Notes

- Audit async and batching use a single-thread scheduled executor (`privacyAuditExecutor`).
- Dead-letter alert monitoring uses its own scheduled executor (`privacyAuditDeadLetterAlertExecutor`).
- Replay store JDBC cleanup is throttled via a configurable cleanup interval and batch size.

## Auto-Configuration Highlights

- `privacy.guard.enabled` gates the entire starter.
- `privacy.guard.tenant.*` enables tenant-aware masking via request-header propagation and configured tenant policies.
- `privacy.guard.audit.repository-type` and `privacy.guard.audit.dead-letter.repository-type` select repository implementations.
- JDBC schema initialization is explicit and conditional on `JdbcOperations` and `*.initialize-schema` flags.
- Receiver verification requires a user-defined `PrivacyAuditDeadLetterWebhookVerificationSettings` bean; once present, replay store and filter/interceptor components can be auto-configured.

## Notes on Extensibility

- Types marked with `@StableSpi` are the supported extension surface for the current minor line, including `MaskingStrategy`, tenant policy interfaces, repository/publisher interfaces, alert callbacks, replay-store interfaces, and their directly coupled carrier types.
- Custom masking is supported via `MaskingStrategy` SPI (ordered injection).
- Additional repositories can be wired by providing custom `PrivacyAuditRepository`, `PrivacyAuditQueryRepository`, or `PrivacyAuditDeadLetterRepository` beans.
- Alert delivery can be extended by implementing `PrivacyAuditDeadLetterAlertCallback`.
- Replay protection can be customized by implementing `PrivacyAuditDeadLetterWebhookReplayStore`.
- Auto-configuration classes, built-in repository implementations, metrics binders, servlet adapters, and schema helpers are internal runtime wiring rather than stable SPI.
