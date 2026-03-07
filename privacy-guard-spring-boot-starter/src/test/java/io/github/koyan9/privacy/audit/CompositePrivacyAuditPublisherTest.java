/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompositePrivacyAuditPublisherTest {

    @Test
    void publishesToAllDelegates() {
        List<PrivacyAuditEvent> publishedByFirst = new ArrayList<>();
        List<PrivacyAuditEvent> publishedBySecond = new ArrayList<>();
        PrivacyAuditEvent event = new PrivacyAuditEvent(Instant.now(), "READ", "Patient", "demo", "actor", "OK", java.util.Map.of());

        CompositePrivacyAuditPublisher publisher = new CompositePrivacyAuditPublisher(List.of(
                publishedByFirst::add,
                publishedBySecond::add
        ));

        publisher.publish(event);

        assertEquals(List.of(event), publishedByFirst);
        assertEquals(List.of(event), publishedBySecond);
    }
}