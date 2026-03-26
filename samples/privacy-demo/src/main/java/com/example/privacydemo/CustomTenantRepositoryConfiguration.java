/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("custom-tenant-native")
class CustomTenantRepositoryConfiguration {

    @Bean
    CustomTenantAuditRepository customTenantAuditRepository() {
        return new CustomTenantAuditRepository();
    }

    @Bean
    CustomTenantDeadLetterRepository customTenantDeadLetterRepository() {
        return new CustomTenantDeadLetterRepository();
    }
}
