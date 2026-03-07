/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import io.github.koyan9.privacy.core.MaskingService;
import io.github.koyan9.privacy.core.SensitiveData;

public class MaskingBeanPropertyWriter extends BeanPropertyWriter {

    private final MaskingService maskingService;
    private final SensitiveData sensitiveData;

    public MaskingBeanPropertyWriter(
            BeanPropertyWriter base,
            MaskingService maskingService,
            SensitiveData sensitiveData
    ) {
        super(base);
        this.maskingService = maskingService;
        this.sensitiveData = sensitiveData;
    }

    @Override
    public void serializeAsField(Object bean, JsonGenerator gen, SerializerProvider provider) throws Exception {
        Object value = get(bean);
        if (!(value instanceof String stringValue)) {
            super.serializeAsField(bean, gen, provider);
            return;
        }

        String masked = maskingService.mask(stringValue, sensitiveData);
        gen.writeFieldName(getName());
        gen.writeString(masked);
    }
}
