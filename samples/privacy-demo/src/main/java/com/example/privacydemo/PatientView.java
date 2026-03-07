/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import io.github.koyan9.privacy.core.SensitiveData;
import io.github.koyan9.privacy.core.SensitiveType;

public record PatientView(
        @SensitiveData(type = SensitiveType.NAME) String patientName,
        @SensitiveData(type = SensitiveType.PHONE) String phone,
        @SensitiveData(type = SensitiveType.ID_CARD) String idCard,
        @SensitiveData(type = SensitiveType.EMAIL) String email,
        String note
) {
}