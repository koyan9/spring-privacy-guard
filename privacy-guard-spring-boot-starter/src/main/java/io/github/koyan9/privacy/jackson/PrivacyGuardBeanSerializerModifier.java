/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.jackson;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import io.github.koyan9.privacy.core.MaskingService;
import io.github.koyan9.privacy.core.SensitiveData;

import java.util.ArrayList;
import java.util.List;

public class PrivacyGuardBeanSerializerModifier extends BeanSerializerModifier {

    private final MaskingService maskingService;

    public PrivacyGuardBeanSerializerModifier(MaskingService maskingService) {
        this.maskingService = maskingService;
    }

    @Override
    public List<BeanPropertyWriter> changeProperties(
            SerializationConfig config,
            BeanDescription beanDesc,
            List<BeanPropertyWriter> beanProperties
    ) {
        List<BeanPropertyWriter> writers = new ArrayList<>(beanProperties.size());
        for (BeanPropertyWriter writer : beanProperties) {
            AnnotatedMember member = writer.getMember();
            SensitiveData sensitiveData = member == null ? null : member.getAnnotation(SensitiveData.class);
            if (sensitiveData != null && String.class.isAssignableFrom(writer.getType().getRawClass())) {
                writers.add(new MaskingBeanPropertyWriter(writer, maskingService, sensitiveData));
                continue;
            }
            writers.add(writer);
        }
        return writers;
    }
}
