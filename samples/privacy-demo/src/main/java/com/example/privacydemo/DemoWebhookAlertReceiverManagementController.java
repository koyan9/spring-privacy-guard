/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import io.github.koyan9.privacy.audit.PrivacyAuditService;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterWebhookReplayStore;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
class DemoWebhookAlertReceiverManagementController {

    private static final String ADMIN_ACTOR = "demo-admin";

    private final PrivacyAuditDeadLetterWebhookReplayStore replayStore;
    private final DemoWebhookReceiverProperties properties;
    private final PrivacyAuditService privacyAuditService;

    DemoWebhookAlertReceiverManagementController(
            PrivacyAuditDeadLetterWebhookReplayStore replayStore,
            DemoWebhookReceiverProperties properties,
            PrivacyAuditService privacyAuditService
    ) {
        this.replayStore = replayStore;
        this.properties = properties;
        this.privacyAuditService = privacyAuditService;
    }

    @GetMapping("/demo-alert-receiver/replay-store")
    public Map<String, Object> replayStore(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        int normalizedLimit = Math.max(1, limit);
        int normalizedOffset = Math.max(0, offset);
        Map<String, Instant> snapshot = replayStore.snapshot();
        List<Map<String, Object>> entries = snapshot.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .skip(normalizedOffset)
                .limit(normalizedLimit)
                .map(entry -> Map.<String, Object>of(
                        "nonce", entry.getKey(),
                        "expiresAt", entry.getValue().toString()
                ))
                .toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("count", snapshot.size());
        response.put("limit", normalizedLimit);
        response.put("offset", normalizedOffset);
        response.put("storeFile", properties.getStoreFile());
        response.put("entries", entries);
        recordManagementAction("DEMO_ALERT_REPLAY_STORE_QUERY", Map.of(
                "count", String.valueOf(snapshot.size()),
                "limit", String.valueOf(normalizedLimit),
                "offset", String.valueOf(normalizedOffset)
        ));
        return response;
    }

    @GetMapping("/demo-alert-receiver/replay-store/stats")
    public Map<String, Object> replayStoreStats(
            @RequestParam(defaultValue = "PT5M") Duration expiringWithin
    ) {
        Instant now = Instant.now();
        Map<String, Instant> snapshot = replayStore.snapshot();
        long expiringSoon = snapshot.values().stream()
                .filter(expiry -> !expiry.isBefore(now) && !expiry.isAfter(now.plus(expiringWithin)))
                .count();
        Instant earliest = snapshot.values().stream().min(Comparator.naturalOrder()).orElse(null);
        Instant latest = snapshot.values().stream().max(Comparator.naturalOrder()).orElse(null);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("count", snapshot.size());
        response.put("expiringWithin", expiringWithin.toString());
        response.put("expiringSoon", expiringSoon);
        response.put("earliestExpiry", earliest == null ? null : earliest.toString());
        response.put("latestExpiry", latest == null ? null : latest.toString());
        response.put("storeFile", properties.getStoreFile());
        recordManagementAction("DEMO_ALERT_REPLAY_STORE_STATS_QUERY", Map.of(
                "count", String.valueOf(snapshot.size()),
                "expiringWithin", expiringWithin.toString(),
                "expiringSoon", String.valueOf(expiringSoon)
        ));
        return response;
    }

    @DeleteMapping("/demo-alert-receiver/replay-store")
    public Map<String, Object> clearReplayStore() {
        int cleared = replayStore.snapshot().size();
        replayStore.clear();
        recordManagementAction("DEMO_ALERT_REPLAY_STORE_CLEAR", Map.of("cleared", String.valueOf(cleared)));
        return Map.of("cleared", cleared);
    }

    private void recordManagementAction(String action, Map<String, String> details) {
        privacyAuditService.record(action, "DemoWebhookAlertReplayStore", "receiver", ADMIN_ACTOR, "SUCCESS", details);
    }
}
