/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.StableSpi;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@StableSpi
public record PrivacyAuditDeadLetterEntry(
        Long id,
        Instant failedAt,
        int attempts,
        String errorType,
        String errorMessage,
        Instant occurredAt,
        String action,
        String resourceType,
        String resourceId,
        String actor,
        String outcome,
        Map<String, String> details
) {

    public static final String REPLAY_SOURCE_KEY = "_auditReplaySource";
    public static final String REPLAY_DEAD_LETTER_ID_KEY = "_auditReplayDeadLetterId";
    public static final String REPLAY_FAILED_AT_KEY = "_auditReplayFailedAt";
    public static final String REPLAY_ATTEMPTS_KEY = "_auditReplayAttempts";
    public static final String REPLAY_ERROR_TYPE_KEY = "_auditReplayErrorType";
    public static final String REPLAY_SOURCE_VALUE = "DEAD_LETTER";

    public PrivacyAuditDeadLetterEntry {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static PrivacyAuditDeadLetterEntry from(PrivacyAuditEvent event, int attempts, RuntimeException exception) {
        return new PrivacyAuditDeadLetterEntry(
                null,
                Instant.now(),
                attempts,
                exception == null ? IllegalStateException.class.getName() : exception.getClass().getName(),
                exception == null ? "Unknown privacy audit publishing failure" : exception.getMessage(),
                event.occurredAt(),
                event.action(),
                event.resourceType(),
                event.resourceId(),
                event.actor(),
                event.outcome(),
                event.details()
        );
    }

    public PrivacyAuditEvent toAuditEvent() {
        return new PrivacyAuditEvent(
                occurredAt,
                action,
                resourceType,
                resourceId,
                actor,
                outcome,
                details
        );
    }

    public PrivacyAuditEvent toReplayAuditEvent() {
        Map<String, String> replayDetails = new LinkedHashMap<>(details);
        replayDetails.put(REPLAY_SOURCE_KEY, REPLAY_SOURCE_VALUE);
        replayDetails.put(REPLAY_DEAD_LETTER_ID_KEY, id == null ? "UNKNOWN" : String.valueOf(id));
        replayDetails.put(REPLAY_FAILED_AT_KEY, failedAt == null ? null : failedAt.toString());
        replayDetails.put(REPLAY_ATTEMPTS_KEY, String.valueOf(attempts));
        replayDetails.put(REPLAY_ERROR_TYPE_KEY, errorType);
        return new PrivacyAuditEvent(
                occurredAt,
                action,
                resourceType,
                resourceId,
                actor,
                outcome,
                replayDetails
        );
    }
}
