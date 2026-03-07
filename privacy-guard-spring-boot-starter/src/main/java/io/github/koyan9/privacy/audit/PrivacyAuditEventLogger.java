/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

public class PrivacyAuditEventLogger {

    private static final Logger log = LoggerFactory.getLogger(PrivacyAuditEventLogger.class);

    @EventListener
    public void onPrivacyAuditEvent(PrivacyAuditEvent event) {
        log.info(
                "privacy-audit action={} resourceType={} resourceId={} actor={} outcome={} details={}",
                event.action(),
                event.resourceType(),
                event.resourceId(),
                event.actor(),
                event.outcome(),
                event.details()
        );
    }
}