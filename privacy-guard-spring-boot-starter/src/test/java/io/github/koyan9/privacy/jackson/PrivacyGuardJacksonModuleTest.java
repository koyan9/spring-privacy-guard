/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.koyan9.privacy.core.MaskingService;
import io.github.koyan9.privacy.core.SensitiveData;
import io.github.koyan9.privacy.core.SensitiveType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivacyGuardJacksonModuleTest {

    @Test
    void masksAnnotatedStringFieldsDuringSerialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new PrivacyGuardModule(new MaskingService()));

        PatientView payload = new PatientView("Alice", "13800138000", "alice@example.com", "normal");
        String json = mapper.writeValueAsString(payload);

        assertTrue(json.contains("\"patientName\":\"A***e\""));
        assertTrue(json.contains("\"phone\":\"138****8000\""));
        assertTrue(json.contains("\"email\":\"a****@example.com\""));
        assertTrue(json.contains("\"note\":\"normal\""));
    }

    static class PatientView {

        @SensitiveData(type = SensitiveType.NAME)
        private final String patientName;

        @SensitiveData(type = SensitiveType.PHONE)
        private final String phone;

        @SensitiveData(type = SensitiveType.EMAIL)
        private final String email;

        private final String note;

        PatientView(String patientName, String phone, String email, String note) {
            this.patientName = patientName;
            this.phone = phone;
            this.email = email;
            this.note = note;
        }

        public String getPatientName() {
            return patientName;
        }

        public String getPhone() {
            return phone;
        }

        public String getEmail() {
            return email;
        }

        public String getNote() {
            return note;
        }
    }
}