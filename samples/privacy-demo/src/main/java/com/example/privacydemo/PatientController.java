/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import io.github.koyan9.privacy.audit.PrivacyAuditService;
import io.github.koyan9.privacy.logging.PrivacyLogger;
import io.github.koyan9.privacy.logging.PrivacyLoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
class PatientController {

    private final PrivacyAuditService privacyAuditService;
    private final PrivacyLogger log;

    PatientController(PrivacyAuditService privacyAuditService, PrivacyLoggerFactory privacyLoggerFactory) {
        this.privacyAuditService = privacyAuditService;
        this.log = privacyLoggerFactory.getLogger(PatientController.class);
    }

    @GetMapping("/patients/demo")
    public PatientView patient() {
        log.info(
                "loading patient phone={} email={} idCard={}",
                "13800138000",
                "alice@example.com",
                "110101199001011234"
        );

        privacyAuditService.record(
                "PATIENT_READ",
                "Patient",
                "demo-patient-13800138000",
                "alice@example.com",
                "SUCCESS",
                Map.of(
                        "phone", "13800138000",
                        "idCard", "110101199001011234"
                )
        );

        return new PatientView(
                "Alice",
                "13800138000",
                "110101199001011234",
                "alice@example.com",
                "raw-note"
        );
    }
}