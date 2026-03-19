/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import io.github.koyan9.privacy.audit.PrivacyTenantAuditManagementService;
import io.github.koyan9.privacy.core.PrivacyTenantProvider;
import io.github.koyan9.privacy.autoconfigure.PrivacyGuardProperties;
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

    DemoTenantManagementController(
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantAuditManagementService managementService,
            PrivacyGuardProperties properties
    ) {
        this.tenantProvider = tenantProvider;
        this.managementService = managementService;
        this.properties = properties;
    }

    @GetMapping("/demo-tenants/current")
    public Map<String, Object> currentTenant() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tenantModeEnabled", properties.getTenant().isEnabled());
        response.put("headerName", properties.getTenant().getHeaderName());
        response.put("defaultTenant", properties.getTenant().getDefaultTenant());
        response.put("currentTenant", tenantProvider.currentTenantId());
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
            tenants.add(item);
        }
        return Map.of(
                "headerName", properties.getTenant().getHeaderName(),
                "defaultTenant", properties.getTenant().getDefaultTenant(),
                "tenants", tenants
        );
    }
}
