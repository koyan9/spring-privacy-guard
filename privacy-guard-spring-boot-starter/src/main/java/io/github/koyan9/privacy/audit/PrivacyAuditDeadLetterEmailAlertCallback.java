/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.autoconfigure.PrivacyGuardProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

public class PrivacyAuditDeadLetterEmailAlertCallback implements PrivacyAuditDeadLetterAlertCallback {

    private final JavaMailSender mailSender;
    private final PrivacyGuardProperties.AlertEmail properties;

    public PrivacyAuditDeadLetterEmailAlertCallback(
            JavaMailSender mailSender,
            PrivacyGuardProperties.AlertEmail properties
    ) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void handle(PrivacyAuditDeadLetterAlertEvent event) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getFrom());
        message.setTo(properties.getTo());
        message.setSubject(buildSubject(event));
        message.setText(buildBody(event));
        mailSender.send(message);
    }

    private String buildSubject(PrivacyAuditDeadLetterAlertEvent event) {
        String prefix = properties.getSubjectPrefix() == null ? "" : properties.getSubjectPrefix().trim() + " ";
        if (event.recovery()) {
            return prefix + "Dead-letter backlog recovered";
        }
        return prefix + "Dead-letter backlog " + event.currentSnapshot().state().name();
    }

    private String buildBody(PrivacyAuditDeadLetterAlertEvent event) {
        StringBuilder body = new StringBuilder();
        body.append("occurredAt: ").append(event.occurredAt()).append(System.lineSeparator());
        body.append("recovery: ").append(event.recovery()).append(System.lineSeparator());
        body.append("state: ").append(event.currentSnapshot().state()).append(System.lineSeparator());
        body.append("previousState: ").append(event.previousSnapshot() == null ? "none" : event.previousSnapshot().state()).append(System.lineSeparator());
        body.append("total: ").append(event.currentSnapshot().total()).append(System.lineSeparator());
        body.append("warningThreshold: ").append(event.currentSnapshot().warningThreshold()).append(System.lineSeparator());
        body.append("downThreshold: ").append(event.currentSnapshot().downThreshold()).append(System.lineSeparator());
        body.append("byAction: ").append(event.currentSnapshot().byAction()).append(System.lineSeparator());
        body.append("byOutcome: ").append(event.currentSnapshot().byOutcome()).append(System.lineSeparator());
        body.append("byResourceType: ").append(event.currentSnapshot().byResourceType()).append(System.lineSeparator());
        body.append("byErrorType: ").append(event.currentSnapshot().byErrorType()).append(System.lineSeparator());
        return body.toString();
    }
}
