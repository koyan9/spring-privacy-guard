/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterCsvCodec;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterExchangeService;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterRepository;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditManagementService;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditDeadLetterExchangeService;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditDeadLetterQueryService;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditDeadLetterOperationsService;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditQueryService;
import io.github.koyan9.privacy.audit.PrivacyAuditQueryService;
import io.github.koyan9.privacy.audit.PrivacyAuditStatsService;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterService;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterStatsService;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditPolicyResolver;
import io.github.koyan9.privacy.core.PrivacyTenantProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = {PrivacyGuardAutoConfiguration.class, JacksonAutoConfiguration.class})
@ConditionalOnClass(ObjectMapper.class)
@ConditionalOnProperty(prefix = "privacy.guard.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PrivacyGuardDeadLetterExchangeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({ObjectMapper.class, PrivacyAuditDeadLetterRepository.class})
    public PrivacyAuditDeadLetterCsvCodec privacyAuditDeadLetterCsvCodec(ObjectMapper objectMapper) {
        return new PrivacyAuditDeadLetterCsvCodec(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({ObjectMapper.class, PrivacyAuditDeadLetterRepository.class, PrivacyAuditDeadLetterCsvCodec.class})
    public PrivacyAuditDeadLetterExchangeService privacyAuditDeadLetterExchangeService(
            PrivacyAuditDeadLetterRepository repository,
            ObjectMapper objectMapper,
            PrivacyAuditDeadLetterCsvCodec csvCodec
    ) {
        return new PrivacyAuditDeadLetterExchangeService(repository, objectMapper, csvCodec);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({
            PrivacyAuditDeadLetterExchangeService.class,
            PrivacyTenantAuditDeadLetterQueryService.class,
            PrivacyTenantAuditPolicyResolver.class,
            PrivacyAuditDeadLetterCsvCodec.class,
            ObjectMapper.class
    })
    public PrivacyTenantAuditDeadLetterExchangeService privacyTenantAuditDeadLetterExchangeService(
            PrivacyAuditDeadLetterExchangeService exchangeService,
            PrivacyTenantAuditDeadLetterQueryService tenantQueryService,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver,
            PrivacyAuditDeadLetterCsvCodec csvCodec,
            ObjectMapper objectMapper
    ) {
        return new PrivacyTenantAuditDeadLetterExchangeService(
                exchangeService,
                tenantQueryService,
                tenantAuditPolicyResolver,
                csvCodec,
                objectMapper
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({
            PrivacyAuditQueryService.class,
            PrivacyAuditStatsService.class,
            PrivacyTenantAuditQueryService.class,
            PrivacyAuditDeadLetterService.class,
            PrivacyAuditDeadLetterStatsService.class,
            PrivacyTenantAuditDeadLetterQueryService.class,
            PrivacyTenantAuditDeadLetterOperationsService.class,
            PrivacyAuditDeadLetterExchangeService.class,
            PrivacyTenantAuditDeadLetterExchangeService.class,
            PrivacyTenantProvider.class
    })
    public PrivacyTenantAuditManagementService privacyTenantAuditManagementService(
            PrivacyAuditQueryService privacyAuditQueryService,
            PrivacyAuditStatsService privacyAuditStatsService,
            PrivacyTenantAuditQueryService privacyTenantAuditQueryService,
            PrivacyAuditDeadLetterService privacyAuditDeadLetterService,
            PrivacyAuditDeadLetterStatsService privacyAuditDeadLetterStatsService,
            PrivacyTenantAuditDeadLetterQueryService privacyTenantAuditDeadLetterQueryService,
            PrivacyTenantAuditDeadLetterOperationsService privacyTenantAuditDeadLetterOperationsService,
            PrivacyAuditDeadLetterExchangeService privacyAuditDeadLetterExchangeService,
            PrivacyTenantAuditDeadLetterExchangeService privacyTenantAuditDeadLetterExchangeService,
            PrivacyTenantProvider tenantProvider
    ) {
        return new PrivacyTenantAuditManagementService(
                privacyAuditQueryService,
                privacyAuditStatsService,
                privacyTenantAuditQueryService,
                privacyAuditDeadLetterService,
                privacyAuditDeadLetterStatsService,
                privacyTenantAuditDeadLetterQueryService,
                privacyTenantAuditDeadLetterOperationsService,
                privacyAuditDeadLetterExchangeService,
                privacyTenantAuditDeadLetterExchangeService,
                tenantProvider
        );
    }
}
