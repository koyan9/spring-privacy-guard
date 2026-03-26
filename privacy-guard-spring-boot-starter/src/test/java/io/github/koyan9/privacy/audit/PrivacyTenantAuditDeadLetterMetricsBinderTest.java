/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PrivacyTenantAuditDeadLetterMetricsBinderTest {

    @Test
    void exposesTaggedTenantBacklogGauges() {
        PrivacyTenantAuditDeadLetterObservationService observationService = new PrivacyTenantAuditDeadLetterObservationService(
                new PrivacyTenantAuditDeadLetterQueryService(
                        null,
                        null,
                        () -> null,
                        tenantId -> PrivacyTenantAuditPolicy.none()
                ) {
                    @Override
                    public PrivacyAuditDeadLetterStats computeStats(String tenantId, PrivacyAuditDeadLetterQueryCriteria criteria) {
                        return new PrivacyAuditDeadLetterStats(
                                "tenant-a".equals(tenantId) ? 2L : 0L,
                                Map.of("READ", "tenant-a".equals(tenantId) ? 2L : 0L),
                                Map.of(),
                                Map.of(),
                                Map.of()
                        );
                    }
                },
                () -> null,
                1,
                5
        );
        PrivacyTenantAuditDeadLetterMetricsBinder binder = new PrivacyTenantAuditDeadLetterMetricsBinder(
                observationService,
                List.of("tenant-a", "tenant-b")
        );
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        binder.bindTo(registry);

        assertThat(registry.get("privacy.audit.deadletters.tenant.total").tag("tenant", "tenant-a").gauge().value()).isEqualTo(2.0d);
        assertThat(registry.get("privacy.audit.deadletters.tenant.total").tag("tenant", "tenant-b").gauge().value()).isEqualTo(0.0d);
        assertThat(registry.get("privacy.audit.deadletters.tenant.state").tag("tenant", "tenant-a").tag("state", "warning").gauge().value()).isEqualTo(1.0d);
        assertThat(registry.get("privacy.audit.deadletters.tenant.state").tag("tenant", "tenant-b").tag("state", "clear").gauge().value()).isEqualTo(1.0d);
    }
}
