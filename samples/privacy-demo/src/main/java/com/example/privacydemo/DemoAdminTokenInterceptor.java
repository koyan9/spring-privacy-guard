/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import io.github.koyan9.privacy.audit.PrivacyAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
class DemoAdminTokenInterceptor implements HandlerInterceptor {

    private static final String ACCESS_DENIED_ACTION = "AUDIT_DEAD_LETTERS_ACCESS_DENIED";
    private static final String DEFAULT_HEADER_NAME = "X-Demo-Admin-Token";
    private static final String RESOURCE_TYPE = "PrivacyAuditDeadLetter";

    private final DemoAdminProperties properties;
    private final PrivacyAuditService privacyAuditService;

    DemoAdminTokenInterceptor(DemoAdminProperties properties, PrivacyAuditService privacyAuditService) {
        this.properties = properties;
        this.privacyAuditService = privacyAuditService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String expectedToken = properties.getToken();
        if (!StringUtils.hasText(expectedToken)) {
            return true;
        }

        String headerName = StringUtils.hasText(properties.getHeaderName())
                ? properties.getHeaderName()
                : DEFAULT_HEADER_NAME;
        if (expectedToken.equals(request.getHeader(headerName))) {
            return true;
        }

        Map<String, String> details = new LinkedHashMap<>();
        details.put("method", request.getMethod());
        details.put("path", request.getRequestURI());
        details.put("headerName", headerName);
        privacyAuditService.record(
                ACCESS_DENIED_ACTION,
                RESOURCE_TYPE,
                request.getRequestURI(),
                "anonymous",
                "DENIED",
                details
        );

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"Missing or invalid " + headerName + " header\"}");
        return false;
    }
}
