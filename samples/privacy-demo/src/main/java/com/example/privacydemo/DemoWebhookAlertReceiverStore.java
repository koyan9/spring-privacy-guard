/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

@Component
class DemoWebhookAlertReceiverStore {

    private final AtomicReference<ReceivedAlert> lastReceived = new AtomicReference<>();

    void save(ReceivedAlert receivedAlert) {
        lastReceived.set(receivedAlert);
    }

    Optional<ReceivedAlert> lastReceived() {
        return Optional.ofNullable(lastReceived.get());
    }

    void clear() {
        lastReceived.set(null);
    }

    record ReceivedAlert(Instant receivedAt, Map<String, Object> payload) {
    }
}
