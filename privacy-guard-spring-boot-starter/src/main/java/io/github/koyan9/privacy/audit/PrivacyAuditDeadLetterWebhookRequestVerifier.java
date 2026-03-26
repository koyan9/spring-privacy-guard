/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public class PrivacyAuditDeadLetterWebhookRequestVerifier {

    private final PrivacyAuditDeadLetterWebhookVerificationSettings settings;
    private final PrivacyAuditDeadLetterWebhookReplayStore replayStore;

    public PrivacyAuditDeadLetterWebhookRequestVerifier(
            PrivacyAuditDeadLetterWebhookVerificationSettings settings,
            PrivacyAuditDeadLetterWebhookReplayStore replayStore
    ) {
        this.settings = settings;
        this.replayStore = replayStore;
    }

    public void verify(Map<String, String> headers, String body) {
        if (StringUtils.hasText(settings.bearerToken())) {
            String authorization = header(headers, "Authorization");
            String expected = "Bearer " + settings.bearerToken().trim();
            if (!expected.equals(authorization)) {
                throw new PrivacyAuditDeadLetterWebhookVerificationException(
                        PrivacyAuditDeadLetterWebhookVerificationException.Reason.INVALID_AUTHORIZATION,
                        "Missing or invalid Authorization header"
                );
            }
        }
        if (!StringUtils.hasText(settings.signatureSecret())) {
            return;
        }
        String timestamp = header(headers, settings.timestampHeader());
        String nonce = header(headers, settings.nonceHeader());
        String signature = header(headers, settings.signatureHeader());
        if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(signature) || !StringUtils.hasText(nonce)) {
            throw new PrivacyAuditDeadLetterWebhookVerificationException(
                    PrivacyAuditDeadLetterWebhookVerificationException.Reason.MISSING_SIGNATURE_HEADERS,
                    "Missing signature headers"
            );
        }
        long epochSeconds;
        try {
            epochSeconds = Long.parseLong(timestamp);
        } catch (NumberFormatException ex) {
            throw new PrivacyAuditDeadLetterWebhookVerificationException(
                    PrivacyAuditDeadLetterWebhookVerificationException.Reason.INVALID_TIMESTAMP,
                    "Invalid signature timestamp",
                    ex
            );
        }
        Instant now = Instant.now();
        Instant timestampInstant = Instant.ofEpochSecond(epochSeconds);
        Duration skew = Duration.between(timestampInstant, now).abs();
        if (skew.compareTo(settings.maxSkew()) > 0) {
            throw new PrivacyAuditDeadLetterWebhookVerificationException(
                    PrivacyAuditDeadLetterWebhookVerificationException.Reason.EXPIRED_TIMESTAMP,
                    "Signature timestamp expired"
            );
        }
        String expectedSignature = PrivacyAuditDeadLetterWebhookSignatureSupport.sign(
                timestamp + "." + nonce + "." + body,
                settings.signatureSecret(),
                settings.signatureAlgorithm()
        );
        if (!expectedSignature.equalsIgnoreCase(signature)) {
            throw new PrivacyAuditDeadLetterWebhookVerificationException(
                    PrivacyAuditDeadLetterWebhookVerificationException.Reason.INVALID_SIGNATURE,
                    "Invalid signature"
            );
        }
        if (!replayStore.markIfNew(replayNonceKey(nonce), now, settings.maxSkew())) {
            throw new PrivacyAuditDeadLetterWebhookVerificationException(
                    PrivacyAuditDeadLetterWebhookVerificationException.Reason.REPLAY_DETECTED,
                    "Replay detected"
            );
        }
    }

    private String replayNonceKey(String nonce) {
        String namespace = settings.replayNamespace();
        if (!StringUtils.hasText(namespace)) {
            return nonce;
        }
        return namespace.trim() + ":" + nonce;
    }

    private String header(Map<String, String> headers, String name) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
