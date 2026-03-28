/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.autoconfigure.PrivacyGuardProperties;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.InputStream;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TenantScopedPrivacyAuditDeadLetterEmailAlertCallbackTest {

    @Test
    void routesTenantEmailToTenantSpecificRecipient() {
        CapturingMailSender mailSender = new CapturingMailSender();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PrivacyGuardProperties.AlertEmail defaultProperties = new PrivacyGuardProperties.AlertEmail();
        defaultProperties.setFrom("privacy@example.com");
        defaultProperties.setTo("ops@example.com");
        defaultProperties.setSubjectPrefix("[global]");

        TenantScopedPrivacyAuditDeadLetterEmailAlertCallback callback =
                new TenantScopedPrivacyAuditDeadLetterEmailAlertCallback(
                        mailSender,
                        defaultProperties,
                        new MicrometerPrivacyTenantAuditTelemetry(registry),
                        tenantId -> "tenant-a".equals(tenantId)
                                ? new PrivacyTenantDeadLetterAlertRoutePolicy(
                                PrivacyTenantDeadLetterAlertWebhookPolicy.none(),
                                new PrivacyTenantDeadLetterAlertEmailPolicy(
                                        null,
                                        "tenant-a-ops@example.com",
                                        "[tenant-a]"
                                ),
                                PrivacyTenantDeadLetterAlertReceiverPolicy.none()
                        )
                                : PrivacyTenantDeadLetterAlertRoutePolicy.none()
                );

        callback.handle("tenant-a", event());

        assertThat(mailSender.lastMessage).isNotNull();
        assertThat(mailSender.lastMessage.getTo()).containsExactly("tenant-a-ops@example.com");
        assertThat(mailSender.lastMessage.getSubject()).isEqualTo("[tenant-a] Dead-letter backlog WARNING [tenant=tenant-a]");
        assertThat(mailSender.lastMessage.getText()).contains("tenantId: tenant-a");
        assertThat(registry.get("privacy.audit.deadletters.alert.tenant.deliveries")
                .tag("tenant", "tenant-a")
                .tag("channel", "email")
                .tag("outcome", "success")
                .counter()
                .count()).isEqualTo(1.0d);
    }

    @Test
    void fallsBackToDefaultEmailTargetWhenNoTenantRouteExists() {
        CapturingMailSender mailSender = new CapturingMailSender();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PrivacyGuardProperties.AlertEmail defaultProperties = new PrivacyGuardProperties.AlertEmail();
        defaultProperties.setFrom("privacy@example.com");
        defaultProperties.setTo("ops@example.com");

        TenantScopedPrivacyAuditDeadLetterEmailAlertCallback callback =
                new TenantScopedPrivacyAuditDeadLetterEmailAlertCallback(
                        mailSender,
                        defaultProperties,
                        new MicrometerPrivacyTenantAuditTelemetry(registry),
                        PrivacyTenantDeadLetterAlertRoutePolicyResolver.noop()
                );

        callback.handle("tenant-b", event());

        assertThat(mailSender.lastMessage).isNotNull();
        assertThat(mailSender.lastMessage.getTo()).containsExactly("ops@example.com");
        assertThat(mailSender.lastMessage.getSubject()).isEqualTo("[spring-privacy-guard] Dead-letter backlog WARNING [tenant=tenant-b]");
        assertThat(registry.get("privacy.audit.deadletters.alert.tenant.deliveries")
                .tag("tenant", "tenant-b")
                .tag("channel", "email")
                .tag("outcome", "success")
                .counter()
                .count()).isEqualTo(1.0d);
    }

    @Test
    void skipsEmailDeliveryWhenTenantDeliveryPolicyDisablesEmail() {
        CapturingMailSender mailSender = new CapturingMailSender();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PrivacyGuardProperties.AlertEmail defaultProperties = new PrivacyGuardProperties.AlertEmail();
        defaultProperties.setFrom("privacy@example.com");
        defaultProperties.setTo("ops@example.com");

        TenantScopedPrivacyAuditDeadLetterEmailAlertCallback callback =
                new TenantScopedPrivacyAuditDeadLetterEmailAlertCallback(
                        mailSender,
                        defaultProperties,
                        new MicrometerPrivacyTenantAuditTelemetry(registry),
                        tenantId -> new PrivacyTenantDeadLetterAlertRoutePolicy(
                                PrivacyTenantDeadLetterAlertWebhookPolicy.none(),
                                new PrivacyTenantDeadLetterAlertEmailPolicy(
                                        null,
                                        "tenant-a-ops@example.com",
                                        "[tenant-a]"
                                ),
                                PrivacyTenantDeadLetterAlertReceiverPolicy.none()
                        ),
                        tenantId -> new PrivacyTenantDeadLetterAlertDeliveryPolicy(null, null, Boolean.FALSE)
                );

        assertThat(callback.supportsTenant("tenant-a")).isFalse();
        callback.handle("tenant-a", event());

        assertThat(mailSender.lastMessage).isNull();
        assertThat(registry.find("privacy.audit.deadletters.alert.tenant.deliveries")
                .tag("tenant", "tenant-a")
                .tag("channel", "email")
                .counters()).isEmpty();
    }

    private PrivacyAuditDeadLetterAlertEvent event() {
        return new PrivacyAuditDeadLetterAlertEvent(
                Instant.now(),
                new PrivacyAuditDeadLetterBacklogSnapshot(2, 1, 5, PrivacyAuditDeadLetterBacklogState.WARNING, Map.of("READ", 2L), Map.of(), Map.of(), Map.of()),
                null
        );
    }

    static class CapturingMailSender implements JavaMailSender {

        private SimpleMailMessage lastMessage;

        @Override
        public MimeMessage createMimeMessage() {
            throw new UnsupportedOperationException();
        }

        @Override
        public MimeMessage createMimeMessage(InputStream contentStream) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(MimeMessage mimeMessage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(MimeMessage... mimeMessages) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(org.springframework.mail.javamail.MimeMessagePreparator mimeMessagePreparator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(org.springframework.mail.javamail.MimeMessagePreparator... mimeMessagePreparators) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(SimpleMailMessage simpleMessage) {
            this.lastMessage = simpleMessage;
        }

        @Override
        public void send(SimpleMailMessage... simpleMessages) {
            if (simpleMessages.length > 0) {
                this.lastMessage = simpleMessages[0];
            }
        }
    }
}
