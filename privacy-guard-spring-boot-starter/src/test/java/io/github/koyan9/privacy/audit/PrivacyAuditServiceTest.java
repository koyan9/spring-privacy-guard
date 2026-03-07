/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.MaskingService;
import io.github.koyan9.privacy.core.TextMaskingService;
import io.github.koyan9.privacy.logging.PrivacyLogSanitizer;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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
}