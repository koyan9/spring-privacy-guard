/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

public interface PrivacyTenantAuditTelemetry {

    void recordQueryReadPath(String domain, String pathKind);

    void recordWritePath(String domain, String pathKind);

    static PrivacyTenantAuditTelemetry noop() {
        return new PrivacyTenantAuditTelemetry() {
            @Override
            public void recordQueryReadPath(String domain, String pathKind) {
            }

            @Override
            public void recordWritePath(String domain, String pathKind) {
            }
        };
    }
}
