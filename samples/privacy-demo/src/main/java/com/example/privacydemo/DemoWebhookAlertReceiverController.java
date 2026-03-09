/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
class DemoWebhookAlertReceiverController {

    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final DemoWebhookAlertReceiverStore store;

    DemoWebhookAlertReceiverController(
            ObjectMapper objectMapper,
            DemoWebhookAlertReceiverStore store
    ) {
        this.objectMapper = objectMapper;
        this.store = store;
    }

    @PostMapping("/demo-alert-receiver")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> receive(@RequestBody String body) {
        Map<String, Object> payload = parse(body);
        store.save(new DemoWebhookAlertReceiverStore.ReceivedAlert(Instant.now(), payload));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("accepted", true);
        response.put("verified", true);
        response.put("receivedAt", Instant.now().toString());
        return response;
    }

    @GetMapping("/demo-alert-receiver/last")
    public DemoWebhookAlertReceiverStore.ReceivedAlert lastReceived() {
        return store.lastReceived().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No alert received yet"));
    }

    private Map<String, Object> parse(String body) {
        try {
            return objectMapper.readValue(body, PAYLOAD_TYPE);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid alert payload", ex);
        }
    }
}
