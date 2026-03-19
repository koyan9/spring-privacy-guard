/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import io.github.koyan9.privacy.core.StableSpi;

@StableSpi
public interface PrivacyAuditPublisher {

    void publish(PrivacyAuditEvent event);
}
