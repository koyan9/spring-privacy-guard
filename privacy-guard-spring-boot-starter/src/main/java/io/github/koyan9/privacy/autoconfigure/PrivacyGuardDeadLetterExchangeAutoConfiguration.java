/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterCsvCodec;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterExchangeService;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterRepository;
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
}
