/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrivacyAuditDeadLetterWebhookRequestVerifierTest {

    @Test
    void verifiesValidSignedRequest() {
        InMemoryPrivacyAuditDeadLetterWebhookReplayStore replayStore = new InMemoryPrivacyAuditDeadLetterWebhookReplayStore();
        PrivacyAuditDeadLetterWebhookVerificationSettings settings = settings(Duration.ofMinutes(5));
        PrivacyAuditDeadLetterWebhookRequestVerifier verifier = new PrivacyAuditDeadLetterWebhookRequestVerifier(settings, replayStore);
        String body = "{\"state\":\"WARNING\"}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = "nonce-1";
        String signature = PrivacyAuditDeadLetterWebhookSignatureSupport.sign(timestamp + "." + nonce + "." + body, settings.signatureSecret(), settings.signatureAlgorithm());

        verifier.verify(headers(timestamp, nonce, signature), body);

        assertThat(replayStore.snapshot()).containsKey("nonce-1");
    }

    @Test
    void rejectsReplayedNonce() {
        InMemoryPrivacyAuditDeadLetterWebhookReplayStore replayStore = new InMemoryPrivacyAuditDeadLetterWebhookReplayStore();
        PrivacyAuditDeadLetterWebhookVerificationSettings settings = settings(Duration.ofMinutes(5));
        PrivacyAuditDeadLetterWebhookRequestVerifier verifier = new PrivacyAuditDeadLetterWebhookRequestVerifier(settings, replayStore);
        String body = "{\"state\":\"WARNING\"}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = "nonce-1";
        String signature = PrivacyAuditDeadLetterWebhookSignatureSupport.sign(timestamp + "." + nonce + "." + body, settings.signatureSecret(), settings.signatureAlgorithm());

        verifier.verify(headers(timestamp, nonce, signature), body);
        assertThatThrownBy(() -> verifier.verify(headers(timestamp, nonce, signature), body))
                .isInstanceOf(PrivacyAuditDeadLetterWebhookVerificationException.class)
                .extracting(ex -> ((PrivacyAuditDeadLetterWebhookVerificationException) ex).reason())
                .isEqualTo(PrivacyAuditDeadLetterWebhookVerificationException.Reason.REPLAY_DETECTED);
    }

    @Test
    void rejectsExpiredTimestamp() {
        InMemoryPrivacyAuditDeadLetterWebhookReplayStore replayStore = new InMemoryPrivacyAuditDeadLetterWebhookReplayStore();
        PrivacyAuditDeadLetterWebhookVerificationSettings settings = settings(Duration.ofSeconds(1));
        PrivacyAuditDeadLetterWebhookRequestVerifier verifier = new PrivacyAuditDeadLetterWebhookRequestVerifier(settings, replayStore);
        String body = "{\"state\":\"WARNING\"}";
        String timestamp = String.valueOf(Instant.now().minusSeconds(120).getEpochSecond());
        String nonce = "nonce-1";
        String signature = PrivacyAuditDeadLetterWebhookSignatureSupport.sign(timestamp + "." + nonce + "." + body, settings.signatureSecret(), settings.signatureAlgorithm());

        assertThatThrownBy(() -> verifier.verify(headers(timestamp, nonce, signature), body))
                .isInstanceOf(PrivacyAuditDeadLetterWebhookVerificationException.class)
                .extracting(ex -> ((PrivacyAuditDeadLetterWebhookVerificationException) ex).reason())
                .isEqualTo(PrivacyAuditDeadLetterWebhookVerificationException.Reason.EXPIRED_TIMESTAMP);
    }

    @Test
    void rejectsInvalidSignature() {
        InMemoryPrivacyAuditDeadLetterWebhookReplayStore replayStore = new InMemoryPrivacyAuditDeadLetterWebhookReplayStore();
        PrivacyAuditDeadLetterWebhookVerificationSettings settings = settings(Duration.ofMinutes(5));
        PrivacyAuditDeadLetterWebhookRequestVerifier verifier = new PrivacyAuditDeadLetterWebhookRequestVerifier(settings, replayStore);
        String body = "{\"state\":\"WARNING\"}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = "nonce-1";

        assertThatThrownBy(() -> verifier.verify(headers(timestamp, nonce, "invalid"), body))
                .isInstanceOf(PrivacyAuditDeadLetterWebhookVerificationException.class)
                .extracting(ex -> ((PrivacyAuditDeadLetterWebhookVerificationException) ex).reason())
                .isEqualTo(PrivacyAuditDeadLetterWebhookVerificationException.Reason.INVALID_SIGNATURE);
    }

    private PrivacyAuditDeadLetterWebhookVerificationSettings settings(Duration maxSkew) {
        return new PrivacyAuditDeadLetterWebhookVerificationSettings(
                "demo-receiver-token",
                "demo-receiver-secret",
                "HmacSHA256",
                "X-Privacy-Alert-Signature",
                "X-Privacy-Alert-Timestamp",
                "X-Privacy-Alert-Nonce",
                maxSkew
        );
    }

    private Map<String, String> headers(String timestamp, String nonce, String signature) {
        return Map.of(
                "Authorization", "Bearer demo-receiver-token",
                "X-Privacy-Alert-Timestamp", timestamp,
                "X-Privacy-Alert-Nonce", nonce,
                "X-Privacy-Alert-Signature", signature
        );
    }
}
