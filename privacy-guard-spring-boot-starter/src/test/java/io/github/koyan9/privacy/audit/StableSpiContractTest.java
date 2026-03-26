/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.StableSpi;
import io.github.koyan9.privacy.logging.PrivacyTenantLoggingPolicy;
import io.github.koyan9.privacy.logging.PrivacyTenantLoggingPolicyResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class StableSpiContractTest {

    @Test
    void marksStarterStableSpiInterfacesAndCarrierTypes() {
        assertStable(PrivacyAuditPublisher.class);
        assertStable(PrivacyAuditRepository.class);
        assertStable(PrivacyAuditQueryRepository.class);
        assertStable(PrivacyAuditStatsRepository.class);
        assertStable(PrivacyTenantAuditReadRepository.class);
        assertStable(PrivacyTenantAuditWriteRepository.class);
        assertStable(PrivacyAuditDeadLetterRepository.class);
        assertStable(PrivacyAuditDeadLetterStatsRepository.class);
        assertStable(PrivacyTenantAuditDeadLetterReadRepository.class);
        assertStable(PrivacyTenantAuditDeadLetterWriteRepository.class);
        assertStable(PrivacyTenantAuditDeadLetterDeleteRepository.class);
        assertStable(PrivacyTenantAuditDeadLetterReplayRepository.class);
        assertStable(PrivacyAuditDeadLetterHandler.class);
        assertStable(PrivacyAuditDeadLetterAlertCallback.class);
        assertStable(PrivacyTenantAuditDeadLetterAlertCallback.class);
        assertStable(PrivacyAuditDeadLetterWebhookReplayStore.class);
        assertStable(PrivacyAuditDeadLetterWebhookAlertTelemetry.class);
        assertStable(PrivacyAuditDeadLetterWebhookVerificationTelemetry.class);
        assertStable(PrivacyTenantAuditPolicyResolver.class);
        assertStable(PrivacyTenantDeadLetterObservabilityPolicy.class);
        assertStable(PrivacyTenantDeadLetterObservabilityPolicyResolver.class);
        assertStable(PrivacyTenantLoggingPolicy.class);
        assertStable(PrivacyTenantLoggingPolicyResolver.class);
        assertStable(PrivacyAuditEvent.class);
        assertStable(PrivacyAuditQueryCriteria.class);
        assertStable(PrivacyAuditQueryStats.class);
        assertStable(PrivacyAuditDeadLetterEntry.class);
        assertStable(PrivacyAuditDeadLetterQueryCriteria.class);
        assertStable(PrivacyAuditDeadLetterStats.class);
        assertStable(PrivacyTenantAuditPolicy.class);
        assertStable(PrivacyTenantAuditWriteRequest.class);
        assertStable(PrivacyTenantAuditDeadLetterWriteRequest.class);
        assertStable(PrivacyAuditDeadLetterAlertEvent.class);
        assertStable(PrivacyAuditDeadLetterWebhookReplayStoreSnapshot.class);
        assertStable(PrivacyAuditDeadLetterWebhookReplayStoreCleanupSnapshot.class);
        assertStable(WebhookAlertFailureDetail.class);
    }

    private static void assertStable(Class<?> type) {
        assertNotNull(type.getAnnotation(StableSpi.class), () -> type.getName() + " should declare @StableSpi");
    }
}
