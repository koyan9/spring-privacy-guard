/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.tenant;

import io.github.koyan9.privacy.core.PrivacyTenantContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class PrivacyTenantContextFilter extends OncePerRequestFilter {

    private final String headerName;
    private final String defaultTenant;

    public PrivacyTenantContextFilter(String headerName, String defaultTenant) {
        this.headerName = headerName;
        this.defaultTenant = defaultTenant;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String tenantId = request.getHeader(headerName);
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = defaultTenant;
        }
        if (tenantId == null || tenantId.isBlank()) {
            PrivacyTenantContextHolder.clear();
        } else {
            PrivacyTenantContextHolder.setTenantId(tenantId);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            PrivacyTenantContextHolder.clear();
        }
    }
}
