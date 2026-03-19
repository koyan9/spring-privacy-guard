/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.tenant;

import io.github.koyan9.privacy.core.PrivacyTenantContextHolder;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class PrivacyTenantContextFilterTest {

    @Test
    void populatesAndClearsTenantFromHeader() throws Exception {
        PrivacyTenantContextFilter filter = new PrivacyTenantContextFilter("X-Privacy-Tenant", "default-tenant");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Privacy-Tenant", "tenant-a");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse res) {
                assertThat(PrivacyTenantContextHolder.getTenantId()).isEqualTo("tenant-a");
            }
        });

        filter.doFilter(request, response, filterChain);

        assertThat(PrivacyTenantContextHolder.getTenantId()).isNull();
    }

    @Test
    void fallsBackToDefaultTenantWhenHeaderMissing() throws Exception {
        PrivacyTenantContextFilter filter = new PrivacyTenantContextFilter("X-Privacy-Tenant", "tenant-default");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse res) {
                assertThat(PrivacyTenantContextHolder.getTenantId()).isEqualTo("tenant-default");
            }
        });

        filter.doFilter(request, response, filterChain);

        assertThat(PrivacyTenantContextHolder.getTenantId()).isNull();
    }
}
