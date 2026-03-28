/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookSignatureSupport;
import org.springframework.http.HttpHeaders;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

abstract class SampleReceiverRouteTestSupport {

    protected static final String ADMIN_TOKEN = "demo-admin-token";

    protected static final String DEFAULT_RECEIVER_PATH = "/demo-alert-receiver";
    protected static final String TENANT_A_RECEIVER_PATH = "/demo-alert-receiver/tenant-a";
    protected static final String TENANT_B_RECEIVER_PATH = "/demo-alert-receiver/tenant-b";

    protected static final String DEFAULT_RECEIVER_BEARER_TOKEN = "demo-receiver-token";
    protected static final String DEFAULT_RECEIVER_SIGNATURE_SECRET = "demo-receiver-secret";

    protected static final String TENANT_A_RECEIVER_BEARER_TOKEN = "demo-tenant-a-receiver-token";
    protected static final String TENANT_A_RECEIVER_SIGNATURE_SECRET = "demo-tenant-a-receiver-secret";
    protected static final String TENANT_A_REPLAY_NAMESPACE = "tenant-a-receiver";

    protected static final String TENANT_B_RECEIVER_BEARER_TOKEN = "demo-tenant-b-receiver-token";
    protected static final String TENANT_B_RECEIVER_SIGNATURE_SECRET = "demo-tenant-b-receiver-secret";
    protected static final String TENANT_B_REPLAY_NAMESPACE = "tenant-b-receiver";

    protected static final String SIGNATURE_ALGORITHM = "HmacSHA256";

    protected String currentTimestamp() {
        return String.valueOf(Instant.now().getEpochSecond());
    }

    protected String signPayload(String payload, String timestamp, String nonce, String signatureSecret) {
        return PrivacyAuditDeadLetterWebhookSignatureSupport.sign(
                timestamp + "." + nonce + "." + payload,
                signatureSecret,
                SIGNATURE_ALGORITHM
        );
    }

    protected Map<String, String> receiverHeaders(String bearerToken, String timestamp, String nonce, String signature) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + bearerToken);
        headers.put("X-Privacy-Alert-Timestamp", timestamp);
        headers.put("X-Privacy-Alert-Nonce", nonce);
        headers.put("X-Privacy-Alert-Signature", signature);
        return headers;
    }

    protected Map<String, String> adminHeaders() {
        return Map.of("X-Demo-Admin-Token", ADMIN_TOKEN);
    }

    protected Map<String, String> tenantAdminHeaders(String tenantId) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Demo-Admin-Token", ADMIN_TOKEN);
        headers.put("X-Privacy-Tenant", tenantId);
        return headers;
    }

    protected HttpHeaders httpHeaders(Map<String, String> values) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAll(values);
        return headers;
    }
}
