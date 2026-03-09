/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingPrivacyAuditDeadLetterHandler implements PrivacyAuditDeadLetterHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingPrivacyAuditDeadLetterHandler.class);

    @Override
    public void handle(PrivacyAuditEvent event, int attempts, RuntimeException exception) {
        LOGGER.error(
                "Privacy audit event moved to dead letter handling action={} resourceType={} resourceId={} actor={} outcome={} attempts={}",
                event.action(),
                event.resourceType(),
                event.resourceId(),
                event.actor(),
                event.outcome(),
                attempts,
                exception
        );
    }
}
