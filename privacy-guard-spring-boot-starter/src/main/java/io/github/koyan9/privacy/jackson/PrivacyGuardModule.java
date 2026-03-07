/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;
import io.github.koyan9.privacy.core.MaskingService;

public class PrivacyGuardModule extends SimpleModule {

    public PrivacyGuardModule(MaskingService maskingService) {
        setSerializerModifier(new PrivacyGuardBeanSerializerModifier(maskingService));
    }
}
