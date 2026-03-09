/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

public record PrivacyAuditDeadLetterImportResult(
        int received,
        int imported,
        int skippedDuplicates,
        String sha256
) {
}
