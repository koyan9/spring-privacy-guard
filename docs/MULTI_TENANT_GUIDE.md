# Multi-Tenant Guide

This guide covers the current multi-tenant support in `spring-privacy-guard`: request-scoped tenant context, tenant-specific masking and audit policy resolution, and tenant-scoped audit and dead-letter management helpers.

## Scope

The current multi-tenant surface is intentionally narrow and explicit:

- request-header tenant propagation for servlet applications
- per-tenant fallback mask characters and text masking rules
- per-tenant audit detail filtering and tenant tagging
- tenant-scoped audit and dead-letter query, stats, delete, replay, export, and import helpers

Stable event and query contracts remain tenant-agnostic:

- `PrivacyAuditEvent`
- `PrivacyAuditQueryCriteria`
- `PrivacyAuditDeadLetterQueryCriteria`

Tenant scoping is layered on top of those contracts through helper services instead of adding tenant fields to the stable records.

## Request Contract

Enable tenant mode with `privacy.guard.tenant.enabled=true`.

The built-in servlet contract is:

- request header: `X-Privacy-Tenant` by default
- configurable property: `privacy.guard.tenant.header-name`
- optional fallback tenant: `privacy.guard.tenant.default-tenant`

Example:

```yaml
privacy:
  guard:
    tenant:
      enabled: true
      header-name: X-Privacy-Tenant
      default-tenant: public
```

When tenant mode is enabled in a servlet application, the starter registers `PrivacyTenantContextFilter`.
The filter:

- reads the configured tenant header from the incoming request
- falls back to `default-tenant` when the header is missing or blank
- writes the resolved tenant into `PrivacyTenantContextHolder`
- clears the thread-local context after the request completes

The default `PrivacyTenantProvider` bean reads from that context and falls back to the configured default tenant when tenant mode is enabled.

## Policy Configuration

Per-tenant configuration lives under `privacy.guard.tenant.policies`.

Example:

```yaml
privacy:
  guard:
    fallback-mask-char: "*"
    tenant:
      enabled: true
      header-name: X-Privacy-Tenant
      default-tenant: public
      policies:
        public:
          audit:
            attach-tenant-id: true
            tenant-detail-key: tenant
        tenant-a:
          fallback-mask-char: "#"
          text:
            additional-patterns:
              - type: GENERIC
                pattern: EMP\d{4}
          audit:
            include-detail-keys:
              - phone
              - employeeCode
            attach-tenant-id: true
            tenant-detail-key: tenant
        tenant-b:
          fallback-mask-char: X
          audit:
            include-detail-keys:
              - phone
            attach-tenant-id: true
            tenant-detail-key: tenant
```

The two tenant policy records are:

- `PrivacyTenantPolicy`
  - `fallbackMaskChar`
  - `textMaskingRules`
- `PrivacyTenantAuditPolicy`
  - `includeDetailKeys`
  - `excludeDetailKeys`
  - `attachTenantId`
  - `tenantDetailKey`

Current behavior:

- tenant fallback mask characters override the global fallback for built-in masking
- tenant text rules replace the default `TextMaskingService` rule set when configured
- `include-detail-keys` is applied before `exclude-detail-keys`
- when `attach-tenant-id=true`, the tenant tag is appended after detail sanitization
- if the detail map already contains the configured `tenantDetailKey`, the existing value is preserved because the write path uses `putIfAbsent`

## Masking and Text Flow

Tenant-aware masking affects three core runtime paths:

1. `MaskingService`
   Uses `PrivacyTenantProvider` and `PrivacyTenantPolicyResolver` to resolve the current tenant and its fallback mask character before running custom or built-in masking.
2. `TextMaskingService`
   Resolves tenant-specific `TextMaskingRule` lists before scanning free text.
3. `PrivacyAuditService`
   Resolves `PrivacyTenantAuditPolicy` before sanitizing and filtering audit details.

If you only configure properties, the starter builds both tenant resolvers for you from `PrivacyGuardProperties`.

## Stable SPI for Customization

The tenant customization SPI that is marked `@StableSpi` in the current minor line is:

- `PrivacyTenantProvider`
- `PrivacyTenantContextScope`
- `PrivacyTenantContextSnapshot`
- `PrivacyTenantPolicyResolver`
- `PrivacyTenantAwareMaskingStrategy`
- `PrivacyTenantAuditPolicyResolver`
- `PrivacyTenantAuditReadRepository`
- `PrivacyTenantAuditDeadLetterReadRepository`
- `PrivacyTenantAuditWriteRepository`
- `PrivacyTenantAuditDeadLetterWriteRepository`
- carrier types `PrivacyTenantPolicy` and `PrivacyTenantAuditPolicy`

These are the preferred override points when the property model is not enough.
The built-in in-memory and JDBC repositories also implement the tenant-native read repository interfaces.

### Custom Tenant Provider

Override the default provider when your tenant comes from authentication, RPC metadata, or message headers instead of the servlet header filter:

```java
@Bean
PrivacyTenantProvider privacyTenantProvider() {
    return MyTenantContext::currentTenantId;
}
```

In servlet applications, a custom `PrivacyTenantProvider` bean replaces the default provider because the auto-configuration uses `@ConditionalOnMissingBean`.

### Custom Tenant-Aware Masking Strategy

Implement `PrivacyTenantAwareMaskingStrategy` when the same sensitive type needs different masking output per tenant:

```java
@Component
class TenantNameMaskingStrategy implements PrivacyTenantAwareMaskingStrategy {

    @Override
    public boolean supports(String tenantId, MaskingContext context) {
        return context.sensitiveType() == SensitiveType.NAME;
    }

    @Override
    public String mask(String tenantId, String value, MaskingContext context) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String masked = context.maskChar().repeat(Math.max(1, value.length() - 1));
        if ("tenant-a".equalsIgnoreCase(tenantId)) {
            return "TENANT-A-" + value.charAt(0) + masked;
        }
        return value.charAt(0) + masked;
    }
}
```

The sample application contains a full example in `samples/privacy-demo/src/main/java/com/example/privacydemo/DemoNameMaskingStrategy.java`.

## Tenant-Scoped Audit and Dead-Letter Helpers

The tenant-aware service layer is split into focused helpers:

- `PrivacyTenantAuditQueryService`
  - `findByCriteria(tenantId, criteria)`
  - `findForCurrentTenant(criteria)`
  - `computeStats(tenantId, criteria)`
  - `computeStatsForCurrentTenant(criteria)`
- `PrivacyTenantAuditDeadLetterQueryService`
  - tenant-scoped dead-letter reads and stats
- `PrivacyTenantAuditDeadLetterOperationsService`
  - tenant-scoped batch delete and replay
- `PrivacyTenantAuditDeadLetterExchangeService`
  - tenant-scoped JSON and CSV export, import, and manifest generation
- `PrivacyTenantAuditManagementService`
  - convenience facade that combines the audit and dead-letter helpers behind one bean

The persistence side can also opt into tenant-aware write hints:

- `PrivacyTenantAuditWriteRepository`
- `PrivacyTenantAuditDeadLetterWriteRepository`

The important contract boundary is:

- the write path stores tenant information in `PrivacyAuditEvent.details()` and `PrivacyAuditDeadLetterEntry.details()`
- the read helpers filter by the configured `tenantDetailKey`
- the criteria records do not gain a tenant field

This keeps the stable contracts small, but it also means tenant filtering is helper-driven rather than repository-native.
When a repository implements `PrivacyTenantAuditReadRepository` or `PrivacyTenantAuditDeadLetterReadRepository`, the tenant helpers now prefer that native path before falling back to cross-page in-memory filtering.
When a repository implements `PrivacyTenantAuditWriteRepository` or `PrivacyTenantAuditDeadLetterWriteRepository`, the built-in repository publisher, async path, buffered path, and repository-backed dead-letter handler can also pass tenant-aware write hints without changing the stable event types.

### Management Facade Example

```java
@RestController
class AuditOpsController {

    private final PrivacyTenantAuditManagementService managementService;

    AuditOpsController(PrivacyTenantAuditManagementService managementService) {
        this.managementService = managementService;
    }

    @GetMapping("/audit-events")
    List<PrivacyAuditEvent> events(@RequestParam(required = false) String tenant) {
        return managementService.findAuditEvents(tenant, PrivacyAuditQueryCriteria.recent(20));
    }
}
```

The facade is only auto-configured when the full dead-letter exchange stack is present.
If your application only needs tenant-scoped audit reads, `PrivacyTenantAuditQueryService` is still available without the facade.

### Import and Export Semantics

`PrivacyTenantAuditDeadLetterExchangeService` scopes content differently depending on direction:

- export only returns entries for the requested tenant
- manifest totals and checksums are computed on the tenant-scoped export content
- import into a tenant rewrites or appends the configured `tenantDetailKey` in each imported entry to the target tenant

That last point is intentional: importing a `tenant-a` export into `tenant-b` retags the imported dead letters to `tenant-b` while preserving the original resource identifiers and error metadata.

## Sample Application

`samples/privacy-demo` enables tenant mode by default in `samples/privacy-demo/src/main/resources/application.yml`.

The sample exposes:

- public tenant inspection: `GET /demo-tenants/current`
- protected tenant policy summary: `GET /demo-tenants/policies`
- tenant-scoped audit reads:
  - `GET /audit-events?action=PATIENT_READ&tenant=tenant-a`
  - `GET /audit-events/stats?action=PATIENT_READ&tenant=tenant-a`
- tenant-scoped dead-letter management:
  - `GET /audit-dead-letters?tenant=tenant-a`
  - `GET /audit-dead-letters/stats?tenant=tenant-a`
  - `DELETE /audit-dead-letters?tenant=tenant-a`
  - `POST /audit-dead-letters/replay?tenant=tenant-b`
  - `GET /audit-dead-letters/export.json?tenant=tenant-a`
  - `GET /audit-dead-letters/export.manifest?format=json&tenant=tenant-a`
  - `POST /audit-dead-letters/import.json?tenant=tenant-b`

Sample request flow:

```bash
curl -H "X-Privacy-Tenant: tenant-a" http://localhost:8088/patients/demo
curl http://localhost:8088/demo-tenants/current
curl -H "X-Privacy-Tenant: tenant-a" http://localhost:8088/demo-tenants/current
curl -H "X-Demo-Admin-Token: demo-admin-token" http://localhost:8088/demo-tenants/policies
curl -H "X-Demo-Admin-Token: demo-admin-token" "http://localhost:8088/audit-events?action=PATIENT_READ&tenant=tenant-a"
curl -H "X-Demo-Admin-Token: demo-admin-token" "http://localhost:8088/audit-dead-letters/export.json?tenant=tenant-a"
```

Keep the two tenant channels distinct:

- `X-Privacy-Tenant` controls the current request tenant for masking, text sanitization, and audit writes
- the `tenant` query parameter used by the sample management endpoints controls which tenant is selected by the helper services

## Operational Notes

- In non-servlet execution paths such as scheduled jobs, async consumers, or message listeners, `PrivacyTenantContextFilter` does not run. Supply a custom `PrivacyTenantProvider` or use `PrivacyTenantContextHolder`, `PrivacyTenantContextScope`, and `PrivacyTenantContextSnapshot` explicitly around the work.
- Built-in tenant-scoped read helpers preserve the stable query contracts by paging the existing repositories and filtering by `details[tenantDetailKey]`. For large JDBC datasets or strict SQL-level tenant isolation, prefer custom repository beans and your own persistence model.
- Built-in JDBC repositories can also use an optional dedicated tenant column via `privacy.guard.audit.jdbc.tenant-column-name` and `privacy.guard.audit.dead-letter.jdbc.tenant-column-name`. When configured, the repositories copy the configured detail key into that column on write and prefer column-based filtering on read.
- When Micrometer is available, tenant-aware query helpers also emit `privacy.audit.tenant.read.path{domain=*,path=*}` counters so you can see whether reads are using native repository paths or fallback filtering.
- Tenant-aware write paths also emit `privacy.audit.tenant.write.path{domain=*,path=*}` counters so you can distinguish direct repository writes, buffered batch writes, and dead-letter persistence path selection.
- If you attach tenant IDs to audit details, keep the same `tenantDetailKey` across write, query, export, and import flows for predictable filtering.
- The sample protects management endpoints with `X-Demo-Admin-Token: demo-admin-token`. That header is sample-only and not part of the starter SPI.

### Non-Web Context Propagation

For code that runs outside servlet request handling, capture and restore the tenant explicitly:

```java
try (PrivacyTenantContextScope ignored = PrivacyTenantContextHolder.openScope("tenant-a")) {
    PrivacyTenantContextSnapshot snapshot = PrivacyTenantContextHolder.snapshot();
    executor.submit(snapshot.wrap(() -> {
        String tenantId = PrivacyTenantContextHolder.getTenantId();
        // tenantId == "tenant-a"
    }));
}
```

`PrivacyTenantContextSnapshot` is useful when work crosses threads.
`PrivacyTenantContextScope` is useful when you want structured try-with-resources restoration in the current thread.

## Related Docs

- `README.md`
- `README.zh-CN.md`
- `docs/LOGIC_WALKTHROUGH.md`
- `docs/JDBC_PRODUCTION_GUIDE.md`
- `docs/TENANT_ADOPTION_PLAYBOOK.md`
- `samples/privacy-demo/README.md`
