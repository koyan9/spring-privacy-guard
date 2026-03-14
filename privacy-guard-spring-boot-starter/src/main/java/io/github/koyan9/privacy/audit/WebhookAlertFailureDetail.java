/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

public record WebhookAlertFailureDetail(
        String failureType,
        String message,
        int statusCode,
        boolean retryable
) {
}
