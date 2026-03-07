/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import java.util.List;

public class CompositePrivacyAuditPublisher implements PrivacyAuditPublisher {

    private final List<PrivacyAuditPublisher> publishers;

    public CompositePrivacyAuditPublisher(List<PrivacyAuditPublisher> publishers) {
        this.publishers = List.copyOf(publishers);
    }

    @Override
    public void publish(PrivacyAuditEvent event) {
        for (PrivacyAuditPublisher publisher : publishers) {
            publisher.publish(event);
        }
    }
}