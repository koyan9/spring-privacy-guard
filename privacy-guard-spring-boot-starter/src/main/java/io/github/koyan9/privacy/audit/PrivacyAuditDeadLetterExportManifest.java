/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import java.time.Instant;

public record PrivacyAuditDeadLetterExportManifest(
        String format,
        int total,
        Instant generatedAt,
        String sha256
) {
}
