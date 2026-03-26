/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.autoconfigure;

import io.github.koyan9.privacy.audit.MicrometerPrivacyTenantAuditTelemetry;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditTelemetry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(before = PrivacyGuardAutoConfiguration.class)
@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
public class PrivacyGuardTenantObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PrivacyTenantAuditTelemetry.class)
    public PrivacyTenantAuditTelemetry privacyTenantAuditTelemetry(
            ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meterRegistryProvider
    ) {
        return new MicrometerPrivacyTenantAuditTelemetry(meterRegistryProvider::getIfAvailable);
    }
}
