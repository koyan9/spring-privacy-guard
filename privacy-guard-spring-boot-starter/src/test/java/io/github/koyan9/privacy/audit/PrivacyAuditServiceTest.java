/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.MaskingService;
import io.github.koyan9.privacy.core.PrivacyTenantPolicy;
import io.github.koyan9.privacy.core.SensitiveType;
import io.github.koyan9.privacy.core.TextMaskingRule;
import io.github.koyan9.privacy.core.TextMaskingService;
import io.github.koyan9.privacy.logging.PrivacyLogSanitizer;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PrivacyAuditServiceTest {

    @Test
    void publishesSanitizedAuditEvent() {
        AtomicReference<PrivacyAuditEvent> published = new AtomicReference<>();
        PrivacyAuditService service = new PrivacyAuditService(
                published::set,
                new PrivacyLogSanitizer(new TextMaskingService(new MaskingService()))
        );

        PrivacyAuditEvent event = service.record(
                "PATIENT_READ",
                "Patient",
                "demo-13800138000",
                "alice@example.com",
                "SUCCESS",
                Map.of(
                        "phone", "13800138000",
                        "idCard", "110101199001011234"
                )
        );

        assertNotNull(published.get());
        assertEquals("demo-138****8000", event.resourceId());
        assertEquals("a****@example.com", event.actor());
        assertEquals("138****8000", event.details().get("phone"));
        assertEquals("1101**********1234", event.details().get("idCard"));
    }

    @Test
    void appliesTenantAuditPolicyToDetails() {
        AtomicReference<PrivacyAuditEvent> published = new AtomicReference<>();
        java.util.List<TextMaskingRule> tenantRules = new java.util.ArrayList<>(TextMaskingRule.defaults());
        tenantRules.add(new TextMaskingRule(SensitiveType.GENERIC, Pattern.compile("EMP\\d{4}")));
        PrivacyTenantPolicy tenantMaskingPolicy = new PrivacyTenantPolicy(
                "#",
                tenantRules
        );
        PrivacyAuditService service = new PrivacyAuditService(
                published::set,
                new PrivacyLogSanitizer(new TextMaskingService(
                        new MaskingService("*", java.util.List.of(), () -> "tenant-a", tenantId -> tenantMaskingPolicy),
                        TextMaskingRule.defaults(),
                        () -> "tenant-a",
                        tenantId -> tenantMaskingPolicy
                )),
                () -> "tenant-a",
                tenantId -> new PrivacyTenantAuditPolicy(
                        Set.of("phone", "employeeCode"),
                        Set.of("idCard"),
                        true,
                        "tenant"
                )
        );

        PrivacyAuditEvent event = service.record(
                "PATIENT_READ",
                "Patient",
                "demo-13800138000",
                "alice@example.com",
                "SUCCESS",
                Map.of(
                        "phone", "13800138000",
                        "idCard", "110101199001011234",
                        "employeeCode", "EMP1234"
                )
        );

        assertNotNull(published.get());
        assertEquals("138####8000", event.details().get("phone"));
        assertEquals("E#####4", event.details().get("employeeCode"));
        assertEquals("tenant-a", event.details().get("tenant"));
        org.junit.jupiter.api.Assertions.assertFalse(event.details().containsKey("idCard"));
    }

    @Test
    void preservesExplicitTenantDetailWhenAlreadyPresent() {
        AtomicReference<PrivacyAuditEvent> published = new AtomicReference<>();
        PrivacyAuditService service = new PrivacyAuditService(
                published::set,
                new PrivacyLogSanitizer(new TextMaskingService(new MaskingService())),
                () -> "public",
                tenantId -> new PrivacyTenantAuditPolicy(Set.of("tenant", "phone"), Set.of(), true, "tenant")
        );

        PrivacyAuditEvent event = service.record(
                "PATIENT_READ",
                "Patient",
                "demo",
                "actor",
                "SUCCESS",
                Map.of(
                        "tenant", "tenant-a",
                        "phone", "13800138000"
                )
        );

        assertEquals("tenant-a", event.details().get("tenant"));
        assertEquals("138****8000", event.details().get("phone"));
    }
}
