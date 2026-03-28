/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import io.github.koyan9.privacy.audit.PrivacyTenantAuditManagementService;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditDeadLetterObservationService;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterObservationService;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterBacklogSnapshot;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditRepository;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditDeadLetterDeleteRepository;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditDeadLetterReadRepository;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditDeadLetterReplayRepository;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditDeadLetterWriteRepository;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditReadRepository;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditWriteRepository;
import io.github.koyan9.privacy.audit.PrivacyTenantDeadLetterAlertDeliveryPolicy;
import io.github.koyan9.privacy.audit.PrivacyTenantDeadLetterAlertDeliveryPolicyResolver;
import io.github.koyan9.privacy.audit.PrivacyTenantDeadLetterAlertMonitoringPolicy;
import io.github.koyan9.privacy.audit.PrivacyTenantDeadLetterAlertMonitoringPolicyResolver;
import io.github.koyan9.privacy.audit.PrivacyTenantDeadLetterAlertRoutePolicy;
import io.github.koyan9.privacy.audit.PrivacyTenantDeadLetterAlertRoutePolicyResolver;
import io.github.koyan9.privacy.core.PrivacyTenantProvider;
import io.github.koyan9.privacy.autoconfigure.PrivacyGuardProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
class DemoTenantManagementController {

    private final PrivacyTenantProvider tenantProvider;
    private final PrivacyTenantAuditManagementService managementService;
    private final PrivacyGuardProperties properties;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private final ObjectProvider<PrivacyAuditRepository> auditRepositoryProvider;
    private final ObjectProvider<PrivacyAuditDeadLetterRepository> deadLetterRepositoryProvider;
    private final ObjectProvider<PrivacyAuditDeadLetterObservationService> deadLetterObservationServiceProvider;
    private final ObjectProvider<PrivacyTenantAuditDeadLetterObservationService> tenantDeadLetterObservationServiceProvider;
    private final ObjectProvider<DemoDeadLetterAlertCallback> demoDeadLetterAlertCallbackProvider;
    private final ObjectProvider<PrivacyTenantDeadLetterAlertDeliveryPolicyResolver> alertDeliveryPolicyResolverProvider;
    private final ObjectProvider<PrivacyTenantDeadLetterAlertMonitoringPolicyResolver> alertMonitoringPolicyResolverProvider;
    private final ObjectProvider<PrivacyTenantDeadLetterAlertRoutePolicyResolver> alertRoutePolicyResolverProvider;
    private final String instanceId;

    DemoTenantManagementController(
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantAuditManagementService managementService,
            PrivacyGuardProperties properties,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            ObjectProvider<PrivacyAuditRepository> auditRepositoryProvider,
            ObjectProvider<PrivacyAuditDeadLetterRepository> deadLetterRepositoryProvider,
            ObjectProvider<PrivacyAuditDeadLetterObservationService> deadLetterObservationServiceProvider,
            ObjectProvider<PrivacyTenantAuditDeadLetterObservationService> tenantDeadLetterObservationServiceProvider,
            ObjectProvider<DemoDeadLetterAlertCallback> demoDeadLetterAlertCallbackProvider,
            ObjectProvider<PrivacyTenantDeadLetterAlertDeliveryPolicyResolver> alertDeliveryPolicyResolverProvider,
            ObjectProvider<PrivacyTenantDeadLetterAlertMonitoringPolicyResolver> alertMonitoringPolicyResolverProvider,
            ObjectProvider<PrivacyTenantDeadLetterAlertRoutePolicyResolver> alertRoutePolicyResolverProvider,
            @Value("${demo.instance-id:default}") String instanceId
    ) {
        this.tenantProvider = tenantProvider;
        this.managementService = managementService;
        this.properties = properties;
        this.meterRegistryProvider = meterRegistryProvider;
        this.auditRepositoryProvider = auditRepositoryProvider;
        this.deadLetterRepositoryProvider = deadLetterRepositoryProvider;
        this.deadLetterObservationServiceProvider = deadLetterObservationServiceProvider;
        this.tenantDeadLetterObservationServiceProvider = tenantDeadLetterObservationServiceProvider;
        this.demoDeadLetterAlertCallbackProvider = demoDeadLetterAlertCallbackProvider;
        this.alertDeliveryPolicyResolverProvider = alertDeliveryPolicyResolverProvider;
        this.alertMonitoringPolicyResolverProvider = alertMonitoringPolicyResolverProvider;
        this.alertRoutePolicyResolverProvider = alertRoutePolicyResolverProvider;
        this.instanceId = instanceId;
    }

    @GetMapping("/demo-tenants/current")
    public Map<String, Object> currentTenant() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tenantModeEnabled", properties.getTenant().isEnabled());
        response.put("headerName", properties.getTenant().getHeaderName());
        response.put("defaultTenant", properties.getTenant().getDefaultTenant());
        response.put("currentTenant", tenantProvider.currentTenantId());
        response.put("instanceId", instanceId);
        response.put("configuredTenants", new ArrayList<>(properties.getTenant().getPolicies().keySet()));
        response.put("managementFacade", managementService.getClass().getSimpleName());
        return response;
    }

    @GetMapping("/demo-tenants/policies")
    public Map<String, Object> tenantPolicies() {
        List<Map<String, Object>> tenants = new ArrayList<>();
        for (Map.Entry<String, PrivacyGuardProperties.TenantPolicy> entry : properties.getTenant().getPolicies().entrySet()) {
            PrivacyGuardProperties.TenantPolicy tenantPolicy = entry.getValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tenantId", entry.getKey());
            item.put("fallbackMaskChar", tenantPolicy.getFallbackMaskChar());
            item.put("textAdditionalPatternCount", tenantPolicy.getText().getAdditionalPatterns().size());
            item.put("auditIncludeDetailKeys", List.copyOf(tenantPolicy.getAudit().getIncludeDetailKeys()));
            item.put("auditExcludeDetailKeys", List.copyOf(tenantPolicy.getAudit().getExcludeDetailKeys()));
            item.put("auditAttachTenantId", tenantPolicy.getAudit().isAttachTenantId());
            item.put("auditTenantDetailKey", tenantPolicy.getAudit().getTenantDetailKey());
            item.put("deadLetterWarningThreshold", tenantPolicy.getObservability().getDeadLetter().getWarningThreshold());
            item.put("deadLetterDownThreshold", tenantPolicy.getObservability().getDeadLetter().getDownThreshold());
            item.put("deadLetterNotifyOnRecovery", tenantPolicy.getObservability().getDeadLetter().getNotifyOnRecovery());
            item.put("loggingMdcEnabledOverride", tenantPolicy.getLogging().getMdc().getEnabled());
            item.put("loggingMdcIncludeKeys", toList(tenantPolicy.getLogging().getMdc().getIncludeKeys()));
            item.put("loggingMdcExcludeKeys", toList(tenantPolicy.getLogging().getMdc().getExcludeKeys()));
            item.put("loggingStructuredEnabledOverride", tenantPolicy.getLogging().getStructured().getEnabled());
            item.put("loggingStructuredIncludeKeys", toList(tenantPolicy.getLogging().getStructured().getIncludeKeys()));
            item.put("loggingStructuredExcludeKeys", toList(tenantPolicy.getLogging().getStructured().getExcludeKeys()));
            PrivacyTenantDeadLetterAlertMonitoringPolicy alertMonitoringPolicy = alertMonitoringPolicyResolver().resolve(entry.getKey());
            item.put("deadLetterAlertEnabledOverride", alertMonitoringPolicy.enabled());
            PrivacyTenantDeadLetterAlertDeliveryPolicy alertDeliveryPolicy = alertDeliveryPolicyResolver().resolve(entry.getKey());
            item.put("deadLetterAlertLoggingEnabledOverride", alertDeliveryPolicy.loggingEnabled());
            item.put("deadLetterAlertWebhookEnabledOverride", alertDeliveryPolicy.webhookEnabled());
            item.put("deadLetterAlertEmailEnabledOverride", alertDeliveryPolicy.emailEnabled());
            PrivacyTenantDeadLetterAlertRoutePolicy alertRoutePolicy = alertRoutePolicyResolver().resolve(entry.getKey());
            item.put("deadLetterAlertWebhookUrl", alertRoutePolicy.webhook().url());
            item.put("deadLetterAlertEmailTo", alertRoutePolicy.email().to());
            item.put("deadLetterAlertEmailSubjectPrefix", alertRoutePolicy.email().subjectPrefix());
            item.put("deadLetterAlertReceiverPathPattern", alertRoutePolicy.receiver().pathPattern());
            item.put("deadLetterAlertReceiverReplayNamespace", alertRoutePolicy.receiver().replayNamespace());
            tenants.add(item);
        }
        return Map.of(
                "headerName", properties.getTenant().getHeaderName(),
                "defaultTenant", properties.getTenant().getDefaultTenant(),
                "tenants", tenants
        );
    }

    @GetMapping("/demo-tenants/observability")
    public Map<String, Object> tenantObservability() {
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("available", meterRegistry != null);
        response.put("instanceId", instanceId);
        response.put("auditRepositoryType", properties.getAudit().getRepositoryType());
        response.put("deadLetterRepositoryType", properties.getAudit().getDeadLetter().getRepositoryType());
        response.put("repositoryImplementations", repositoryImplementationsView());
        response.put("repositoryCapabilities", repositoryCapabilitiesView());
        response.put("expectedPaths", expectedPathsView());
        response.put("readPaths", metricGroups(
                meterRegistry,
                "privacy.audit.tenant.read.path",
                Map.of(
                        "audit", "audit",
                        "auditStats", "audit_stats",
                        "deadLetter", "dead_letter",
                        "deadLetterStats", "dead_letter_stats",
                        "deadLetterFindById", "dead_letter_find_by_id",
                        "deadLetterExport", "dead_letter_export",
                        "deadLetterManifest", "dead_letter_manifest"
                )
        ));
        response.put("writePaths", metricGroups(
                meterRegistry,
                "privacy.audit.tenant.write.path",
                Map.of(
                        "auditWrite", "audit_write",
                        "auditBatchWrite", "audit_batch_write",
                        "deadLetterWrite", "dead_letter_write",
                        "deadLetterImport", "dead_letter_import",
                        "deadLetterDelete", "dead_letter_delete",
                        "deadLetterDeleteById", "dead_letter_delete_by_id",
                        "deadLetterReplay", "dead_letter_replay",
                        "deadLetterReplayById", "dead_letter_replay_by_id"
                )
        ));
        response.put("tenantOperationalMetrics", tenantOperationalMetricsView(meterRegistry));
        response.put("receiverReplayStore", replayStoreView());
        response.put("deadLetterBacklog", deadLetterBacklogView());
        response.put("tenantAlerting", tenantAlertingView());
        response.put("actuatorQueries", List.of(
                "/actuator/health",
                "/actuator/metrics/privacy.audit.tenant.read.path?tag=domain:audit&tag=path:native",
                "/actuator/metrics/privacy.audit.tenant.read.path?tag=domain:audit_stats&tag=path:native",
                "/actuator/metrics/privacy.audit.tenant.read.path?tag=domain:dead_letter_export&tag=path:native",
                "/actuator/metrics/privacy.audit.tenant.read.path?tag=domain:dead_letter_manifest&tag=path:native",
                "/actuator/metrics/privacy.audit.tenant.read.path?tag=domain:dead_letter_find_by_id&tag=path:native",
                "/actuator/metrics/privacy.audit.tenant.write.path?tag=domain:audit_write&tag=path:native",
                "/actuator/metrics/privacy.audit.tenant.write.path?tag=domain:audit_batch_write&tag=path:fallback",
                "/actuator/metrics/privacy.audit.tenant.write.path?tag=domain:dead_letter_write&tag=path:native",
                "/actuator/metrics/privacy.audit.tenant.write.path?tag=domain:dead_letter_import&tag=path:native",
                "/actuator/metrics/privacy.audit.tenant.write.path?tag=domain:dead_letter_delete&tag=path:native",
                "/actuator/metrics/privacy.audit.tenant.write.path?tag=domain:dead_letter_delete_by_id&tag=path:native",
                "/actuator/metrics/privacy.audit.tenant.write.path?tag=domain:dead_letter_replay&tag=path:native",
                "/actuator/metrics/privacy.audit.tenant.write.path?tag=domain:dead_letter_replay_by_id&tag=path:native",
                "/actuator/metrics/privacy.audit.deadletters.alert.tenant.transitions?tag=tenant:tenant-a&tag=state:warning&tag=recovery:false",
                "/actuator/metrics/privacy.audit.deadletters.alert.tenant.deliveries?tag=tenant:tenant-a&tag=channel:logging&tag=outcome:success",
                "/actuator/metrics/privacy.audit.deadletters.receiver.route.failures?tag=route:/demo-alert-receiver&tag=reason:invalid_signature"
        ));
        return response;
    }

    private Map<String, Object> repositoryCapabilitiesView() {
        Map<String, Object> response = new LinkedHashMap<>();
        PrivacyAuditRepository auditRepository = auditRepositoryProvider.getIfAvailable();
        PrivacyAuditDeadLetterRepository deadLetterRepository = deadLetterRepositoryProvider.getIfAvailable();

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put(
                "tenantReadNative",
                auditRepository instanceof PrivacyTenantAuditReadRepository readRepository
                        && readRepository.supportsTenantRead()
        );
        audit.put(
                "tenantWriteNative",
                auditRepository instanceof PrivacyTenantAuditWriteRepository writeRepository
                        && writeRepository.supportsTenantWrite()
        );
        response.put("audit", audit);

        Map<String, Object> deadLetter = new LinkedHashMap<>();
        deadLetter.put(
                "tenantReadNative",
                deadLetterRepository instanceof PrivacyTenantAuditDeadLetterReadRepository readRepository
                        && readRepository.supportsTenantRead()
        );
        deadLetter.put(
                "tenantFindByIdNative",
                deadLetterRepository instanceof PrivacyTenantAuditDeadLetterReadRepository readRepository
                        && readRepository.supportsTenantFindById()
        );
        deadLetter.put(
                "tenantExchangeReadNative",
                deadLetterRepository instanceof PrivacyTenantAuditDeadLetterReadRepository readRepository
                        && readRepository.supportsTenantExchangeRead()
        );
        deadLetter.put(
                "tenantWriteNative",
                deadLetterRepository instanceof PrivacyTenantAuditDeadLetterWriteRepository writeRepository
                        && writeRepository.supportsTenantWrite()
        );
        deadLetter.put(
                "tenantImportNative",
                deadLetterRepository instanceof PrivacyTenantAuditDeadLetterWriteRepository writeRepository
                        && writeRepository.supportsTenantImport()
        );
        deadLetter.put(
                "tenantDeleteNative",
                deadLetterRepository instanceof PrivacyTenantAuditDeadLetterDeleteRepository deleteRepository
                        && deleteRepository.supportsTenantDelete()
        );
        deadLetter.put(
                "tenantDeleteByIdNative",
                deadLetterRepository instanceof PrivacyTenantAuditDeadLetterDeleteRepository deleteRepository
                        && deleteRepository.supportsTenantDeleteById()
        );
        deadLetter.put(
                "tenantReplayNative",
                deadLetterRepository instanceof PrivacyTenantAuditDeadLetterReplayRepository replayRepository
                        && replayRepository.supportsTenantReplay()
        );
        deadLetter.put(
                "tenantReplayByIdNative",
                deadLetterRepository instanceof PrivacyTenantAuditDeadLetterReplayRepository replayRepository
                        && replayRepository.supportsTenantReplayById()
        );
        response.put("deadLetter", deadLetter);
        return response;
    }

    private Map<String, Object> repositoryImplementationsView() {
        Map<String, Object> response = new LinkedHashMap<>();
        PrivacyAuditRepository auditRepository = auditRepositoryProvider.getIfAvailable();
        PrivacyAuditDeadLetterRepository deadLetterRepository = deadLetterRepositoryProvider.getIfAvailable();
        response.put("audit", auditRepository == null ? null : auditRepository.getClass().getSimpleName());
        response.put("deadLetter", deadLetterRepository == null ? null : deadLetterRepository.getClass().getSimpleName());
        return response;
    }

    private Map<String, Object> expectedPathsView() {
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> capabilities = repositoryCapabilitiesView();
        @SuppressWarnings("unchecked")
        Map<String, Object> audit = (Map<String, Object>) capabilities.get("audit");
        @SuppressWarnings("unchecked")
        Map<String, Object> deadLetter = (Map<String, Object>) capabilities.get("deadLetter");

        Map<String, String> read = new LinkedHashMap<>();
        read.put("audit", expectedPath(audit.get("tenantReadNative")));
        read.put("auditStats", expectedPath(audit.get("tenantReadNative")));
        read.put("deadLetter", expectedPath(deadLetter.get("tenantReadNative")));
        read.put("deadLetterStats", expectedPath(deadLetter.get("tenantReadNative")));
        read.put("deadLetterFindById", expectedPath(deadLetter.get("tenantFindByIdNative")));
        read.put("deadLetterExport", expectedPath(deadLetter.get("tenantExchangeReadNative")));
        read.put("deadLetterManifest", expectedPath(deadLetter.get("tenantExchangeReadNative")));
        response.put("read", read);

        Map<String, String> write = new LinkedHashMap<>();
        write.put("auditWrite", expectedPath(audit.get("tenantWriteNative")));
        write.put("auditBatchWrite", expectedPath(audit.get("tenantWriteNative")));
        write.put("deadLetterWrite", expectedPath(deadLetter.get("tenantWriteNative")));
        write.put("deadLetterImport", expectedPath(deadLetter.get("tenantImportNative")));
        write.put("deadLetterDelete", expectedPath(deadLetter.get("tenantDeleteNative")));
        write.put("deadLetterDeleteById", expectedPath(deadLetter.get("tenantDeleteByIdNative")));
        write.put("deadLetterReplay", expectedPath(deadLetter.get("tenantReplayNative")));
        write.put("deadLetterReplayById", expectedPath(deadLetter.get("tenantReplayByIdNative")));
        response.put("write", write);
        return response;
    }

    private Map<String, Object> tenantAlertingView() {
        DemoDeadLetterAlertCallback callback = demoDeadLetterAlertCallbackProvider.getIfAvailable();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("enabled", properties.getAudit().getDeadLetter().getObservability().getAlert().getTenant().isEnabled());
        response.put("tenantIds", effectiveTenantAlertTenantIds());
        response.put("lastTenantAlert", callback == null
                ? null
                : callback.lastTenantAlert()
                .map(record -> Map.<String, Object>of(
                        "tenantId", record.tenantId(),
                        "recovery", record.event().recovery(),
                        "snapshot", snapshotView(record.event().currentSnapshot())
                ))
                .orElse(null));
        return response;
    }

    private Map<String, Object> replayStoreView() {
        PrivacyGuardProperties.AlertReceiverReplayStore replayStore = properties.getAudit()
                .getDeadLetter()
                .getObservability()
                .getAlert()
                .getReceiver()
                .getReplayStore();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("backend", replayStoreBackend(replayStore));
        response.put("namespace", replayStore.getNamespace());
        response.put("filePath", replayStore.getFile().isEnabled() ? replayStore.getFile().getPath() : null);
        response.put("jdbcTable", replayStore.getJdbc().isEnabled() ? replayStore.getJdbc().getTableName() : null);
        response.put("redisKeyPrefix", replayStore.getRedis().isEnabled() ? replayStore.getRedis().getKeyPrefix() : null);
        return response;
    }

    private Map<String, Object> tenantOperationalMetricsView(MeterRegistry meterRegistry) {
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> alertTransitions = new LinkedHashMap<>();
        Map<String, Object> alertDeliveries = new LinkedHashMap<>();
        for (String tenantId : properties.getTenant().getPolicies().keySet()) {
            Map<String, Object> transitionCounts = new LinkedHashMap<>();
            transitionCounts.put("warning", counterValue(
                    meterRegistry,
                    "privacy.audit.deadletters.alert.tenant.transitions",
                    "tenant",
                    tenantId,
                    "state",
                    "warning",
                    "recovery",
                    "false"
            ));
            transitionCounts.put("down", counterValue(
                    meterRegistry,
                    "privacy.audit.deadletters.alert.tenant.transitions",
                    "tenant",
                    tenantId,
                    "state",
                    "down",
                    "recovery",
                    "false"
            ));
            transitionCounts.put("recoveries", counterValue(
                    meterRegistry,
                    "privacy.audit.deadletters.alert.tenant.transitions",
                    "tenant",
                    tenantId,
                    "state",
                    "clear",
                    "recovery",
                    "true"
            ));
            alertTransitions.put(tenantId, transitionCounts);

            Map<String, Object> deliveryCounts = new LinkedHashMap<>();
            deliveryCounts.put("loggingSuccess", counterValue(
                    meterRegistry,
                    "privacy.audit.deadletters.alert.tenant.deliveries",
                    "tenant",
                    tenantId,
                    "channel",
                    "logging",
                    "outcome",
                    "success"
            ));
            deliveryCounts.put("webhookSuccess", counterValue(
                    meterRegistry,
                    "privacy.audit.deadletters.alert.tenant.deliveries",
                    "tenant",
                    tenantId,
                    "channel",
                    "webhook",
                    "outcome",
                    "success"
            ));
            deliveryCounts.put("webhookFailure", counterValue(
                    meterRegistry,
                    "privacy.audit.deadletters.alert.tenant.deliveries",
                    "tenant",
                    tenantId,
                    "channel",
                    "webhook",
                    "outcome",
                    "failure"
            ));
            deliveryCounts.put("emailSuccess", counterValue(
                    meterRegistry,
                    "privacy.audit.deadletters.alert.tenant.deliveries",
                    "tenant",
                    tenantId,
                    "channel",
                    "email",
                    "outcome",
                    "success"
            ));
            deliveryCounts.put("emailFailure", counterValue(
                    meterRegistry,
                    "privacy.audit.deadletters.alert.tenant.deliveries",
                    "tenant",
                    tenantId,
                    "channel",
                    "email",
                    "outcome",
                    "failure"
            ));
            alertDeliveries.put(tenantId, deliveryCounts);
        }
        Map<String, Object> receiverRouteFailures = new LinkedHashMap<>();
        for (String routeTag : receiverRouteTags()) {
            Map<String, Object> routeFailures = new LinkedHashMap<>();
            routeFailures.put("invalidSignature", counterValue(
                    meterRegistry,
                    "privacy.audit.deadletters.receiver.route.failures",
                    "route",
                    routeTag,
                    "reason",
                    "invalid_signature"
            ));
            routeFailures.put("replayDetected", counterValue(
                    meterRegistry,
                    "privacy.audit.deadletters.receiver.route.failures",
                    "route",
                    routeTag,
                    "reason",
                    "replay_detected"
            ));
            routeFailures.put("invalidAuthorization", counterValue(
                    meterRegistry,
                    "privacy.audit.deadletters.receiver.route.failures",
                    "route",
                    routeTag,
                    "reason",
                    "invalid_authorization"
            ));
            receiverRouteFailures.put(routeTag, routeFailures);
        }
        response.put("alertTransitions", alertTransitions);
        response.put("alertDeliveries", alertDeliveries);
        response.put("receiverRouteFailures", receiverRouteFailures);
        return response;
    }

    private Map<String, Object> metricGroups(
            MeterRegistry meterRegistry,
            String metricName,
            Map<String, String> domainMappings
    ) {
        Map<String, Object> groups = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : domainMappings.entrySet()) {
            Map<String, Double> values = new LinkedHashMap<>();
            values.put("native", counterValue(meterRegistry, metricName, entry.getValue(), "native"));
            values.put("fallback", counterValue(meterRegistry, metricName, entry.getValue(), "fallback"));
            groups.put(entry.getKey(), values);
        }
        return groups;
    }

    private Map<String, Object> deadLetterBacklogView() {
        PrivacyAuditDeadLetterObservationService globalObservationService = deadLetterObservationServiceProvider.getIfAvailable();
        PrivacyTenantAuditDeadLetterObservationService tenantObservationService = tenantDeadLetterObservationServiceProvider.getIfAvailable();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("available", globalObservationService != null);
        response.put("currentTenant", tenantProvider.currentTenantId());
        response.put("global", snapshotView(globalObservationService == null ? null : globalObservationService.currentSnapshot()));
        response.put("currentTenantSnapshot", tenantObservationService == null ? null : snapshotView(tenantObservationService.currentSnapshotForCurrentTenant()));

        Map<String, Object> configuredTenants = new LinkedHashMap<>();
        if (tenantObservationService != null) {
            for (String tenantId : properties.getTenant().getPolicies().keySet()) {
                configuredTenants.put(tenantId, snapshotView(tenantObservationService.currentSnapshot(tenantId)));
            }
        }
        response.put("configuredTenants", configuredTenants);
        return response;
    }

    private Map<String, Object> snapshotView(PrivacyAuditDeadLetterBacklogSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", snapshot.total());
        response.put("warningThreshold", snapshot.warningThreshold());
        response.put("downThreshold", snapshot.downThreshold());
        response.put("state", snapshot.state().name());
        response.put("byAction", snapshot.byAction());
        response.put("byOutcome", snapshot.byOutcome());
        response.put("byResourceType", snapshot.byResourceType());
        response.put("byErrorType", snapshot.byErrorType());
        return response;
    }

    private double counterValue(MeterRegistry meterRegistry, String metricName, String domain, String path) {
        if (meterRegistry == null) {
            return 0.0d;
        }
        Counter counter = meterRegistry.find(metricName)
                .tags("domain", domain, "path", path)
                .counter();
        return counter == null ? 0.0d : counter.count();
    }

    private double counterValue(MeterRegistry meterRegistry, String metricName, String... tags) {
        if (meterRegistry == null) {
            return 0.0d;
        }
        Counter counter = meterRegistry.find(metricName)
                .tags(tags)
                .counter();
        return counter == null ? 0.0d : counter.count();
    }

    private List<String> toList(List<String> source) {
        return source == null ? List.of() : List.copyOf(source);
    }

    private String expectedPath(Object capability) {
        return Boolean.TRUE.equals(capability) ? "native" : "fallback";
    }

    private String replayStoreBackend(PrivacyGuardProperties.AlertReceiverReplayStore replayStore) {
        if (replayStore.getRedis().isEnabled()) {
            return "REDIS";
        }
        if (replayStore.getJdbc().isEnabled()) {
            return "JDBC";
        }
        if (replayStore.getFile().isEnabled()) {
            return "FILE";
        }
        return "IN_MEMORY";
    }

    private List<String> receiverRouteTags() {
        java.util.LinkedHashSet<String> routeTags = new java.util.LinkedHashSet<>();
        String defaultFilterPath = properties.getAudit().getDeadLetter().getObservability().getAlert().getReceiver().getFilter().getPathPattern();
        if (defaultFilterPath != null && !defaultFilterPath.isBlank()) {
            routeTags.add(defaultFilterPath.trim());
        }
        String defaultInterceptorPath = properties.getAudit().getDeadLetter().getObservability().getAlert().getReceiver().getInterceptor().getPathPattern();
        if (defaultInterceptorPath != null && !defaultInterceptorPath.isBlank()) {
            routeTags.add(defaultInterceptorPath.trim());
        }
        for (String tenantId : tenantAlertRouteTenantIds()) {
            String pathPattern = alertRoutePolicyResolver().resolve(tenantId).receiver().pathPattern();
            if (pathPattern != null && !pathPattern.isBlank()) {
                routeTags.add(pathPattern.trim());
            }
        }
        return List.copyOf(routeTags);
    }

    private PrivacyTenantDeadLetterAlertRoutePolicyResolver alertRoutePolicyResolver() {
        return alertRoutePolicyResolverProvider.getIfAvailable(PrivacyTenantDeadLetterAlertRoutePolicyResolver::noop);
    }

    private PrivacyTenantDeadLetterAlertDeliveryPolicyResolver alertDeliveryPolicyResolver() {
        return alertDeliveryPolicyResolverProvider.getIfAvailable(PrivacyTenantDeadLetterAlertDeliveryPolicyResolver::noop);
    }

    private PrivacyTenantDeadLetterAlertMonitoringPolicyResolver alertMonitoringPolicyResolver() {
        return alertMonitoringPolicyResolverProvider.getIfAvailable(PrivacyTenantDeadLetterAlertMonitoringPolicyResolver::noop);
    }

    private List<String> effectiveTenantAlertTenantIds() {
        return io.github.koyan9.privacy.autoconfigure.PrivacyGuardDeadLetterObservabilityAutoConfiguration.resolveTenantAlertTenantIds(
                properties,
                alertMonitoringPolicyResolver()
        );
    }

    private List<String> tenantAlertRouteTenantIds() {
        java.util.LinkedHashSet<String> tenantIds = new java.util.LinkedHashSet<>(effectiveTenantAlertTenantIds());
        tenantIds.addAll(properties.getTenant().getPolicies().keySet());
        tenantIds.addAll(properties.getAudit().getDeadLetter().getObservability().getAlert().getTenant().getRoutes().keySet());
        tenantIds.removeIf(value -> value == null || value.isBlank());
        return List.copyOf(tenantIds);
    }
}
