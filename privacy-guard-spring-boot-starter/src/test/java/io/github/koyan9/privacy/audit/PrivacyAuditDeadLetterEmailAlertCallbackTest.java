/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.autoconfigure.PrivacyGuardProperties;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.InputStream;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PrivacyAuditDeadLetterEmailAlertCallbackTest {

    @Test
    void sendsAlertEmail() {
        CapturingMailSender mailSender = new CapturingMailSender();
        PrivacyGuardProperties.AlertEmail properties = new PrivacyGuardProperties.AlertEmail();
        properties.setFrom("privacy@example.com");
        properties.setTo("ops@example.com");
        properties.setSubjectPrefix("[guard]");

        PrivacyAuditDeadLetterEmailAlertCallback callback = new PrivacyAuditDeadLetterEmailAlertCallback(mailSender, properties);
        callback.handle(event(PrivacyAuditDeadLetterBacklogState.WARNING, null, 2));

        assertThat(mailSender.lastMessage).isNotNull();
        assertThat(mailSender.lastMessage.getSubject()).isEqualTo("[guard] Dead-letter backlog WARNING");
        assertThat(mailSender.lastMessage.getText()).contains("state: WARNING").contains("total: 2");
    }

    @Test
    void sendsRecoveryEmail() {
        CapturingMailSender mailSender = new CapturingMailSender();
        PrivacyGuardProperties.AlertEmail properties = new PrivacyGuardProperties.AlertEmail();
        properties.setFrom("privacy@example.com");
        properties.setTo("ops@example.com");

        PrivacyAuditDeadLetterEmailAlertCallback callback = new PrivacyAuditDeadLetterEmailAlertCallback(mailSender, properties);
        callback.handle(event(PrivacyAuditDeadLetterBacklogState.CLEAR, PrivacyAuditDeadLetterBacklogState.WARNING, 0));

        assertThat(mailSender.lastMessage).isNotNull();
        assertThat(mailSender.lastMessage.getSubject()).isEqualTo("[spring-privacy-guard] Dead-letter backlog recovered");
        assertThat(mailSender.lastMessage.getText()).contains("recovery: true").contains("previousState: WARNING");
    }

    private PrivacyAuditDeadLetterAlertEvent event(
            PrivacyAuditDeadLetterBacklogState state,
            PrivacyAuditDeadLetterBacklogState previousState,
            long total
    ) {
        PrivacyAuditDeadLetterBacklogSnapshot previous = previousState == null
                ? null
                : new PrivacyAuditDeadLetterBacklogSnapshot(1, 1, 5, previousState, Map.of(), Map.of(), Map.of(), Map.of());
        return new PrivacyAuditDeadLetterAlertEvent(
                Instant.now(),
                new PrivacyAuditDeadLetterBacklogSnapshot(total, 1, 5, state, Map.of("READ", total), Map.of(), Map.of(), Map.of()),
                previous
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
