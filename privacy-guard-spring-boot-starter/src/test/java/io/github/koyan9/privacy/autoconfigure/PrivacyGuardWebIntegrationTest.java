/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.autoconfigure;

import io.github.koyan9.privacy.core.SensitiveData;
import io.github.koyan9.privacy.core.SensitiveType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = {PrivacyGuardWebIntegrationTest.TestApplication.class, PrivacyGuardWebIntegrationTest.PatientController.class},
        properties = {
                "privacy.guard.enabled=true",
                "privacy.guard.audit.enabled=false"
        }
)
@AutoConfigureMockMvc
class PrivacyGuardWebIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void masksAnnotatedJsonFieldsInMvcResponses() throws Exception {
        mockMvc.perform(get("/patients/demo"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "patientName": "A***e",
                          "phone": "138****8000",
                          "idCard": "1101**********1234",
                          "email": "a****@example.com",
                          "note": "normal"
                        }
                        """));
    }

    @SpringBootApplication
    static class TestApplication {
    }

    @RestController
    static class PatientController {

        @GetMapping("/patients/demo")
        PatientView patient() {
            return new PatientView(
                    "Alice",
                    "13800138000",
                    "110101199001011234",
                    "alice@example.com",
                    "normal"
            );
        }
    }

    record PatientView(
            @SensitiveData(type = SensitiveType.NAME) String patientName,
            @SensitiveData(type = SensitiveType.PHONE) String phone,
            @SensitiveData(type = SensitiveType.ID_CARD) String idCard,
            @SensitiveData(type = SensitiveType.EMAIL) String email,
            String note
    ) {
    }
}
