/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.koyan9.privacy.audit.FilePrivacyAuditDeadLetterWebhookReplayStore;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStore;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookVerificationSettings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties(DemoWebhookReceiverProperties.class)
class DemoWebhookReceiverConfiguration {

    @Bean
    PrivacyAuditDeadLetterWebhookVerificationSettings demoWebhookVerificationSettings(
            DemoWebhookReceiverProperties properties
    ) {
        return new PrivacyAuditDeadLetterWebhookVerificationSettings(
                properties.getBearerToken(),
                properties.getSignatureSecret(),
                properties.getSignatureAlgorithm(),
                properties.getSignatureHeader(),
                properties.getTimestampHeader(),
                properties.getNonceHeader(),
                properties.getMaxSkew()
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "demo.alert.receiver", name = "store-file")
    PrivacyAuditDeadLetterWebhookReplayStore demoWebhookReplayStore(
            DemoWebhookReceiverProperties properties,
            ObjectMapper objectMapper
    ) {
        return new FilePrivacyAuditDeadLetterWebhookReplayStore(Path.of(properties.getStoreFile().trim()), objectMapper);
    }
}
