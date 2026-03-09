/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StreamUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

public class PrivacyAuditDeadLetterWebhookVerificationInterceptor implements HandlerInterceptor {

    private final PrivacyAuditDeadLetterWebhookRequestVerifier verifier;
    private final String pathPattern;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public PrivacyAuditDeadLetterWebhookVerificationInterceptor(
            PrivacyAuditDeadLetterWebhookRequestVerifier verifier,
            String pathPattern
    ) {
        this.verifier = verifier;
        this.pathPattern = pathPattern;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !pathMatcher.match(pathPattern, request.getRequestURI())) {
            return true;
        }
        String body = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
        try {
            verifier.verify(headers(request), body);
            return true;
        } catch (PrivacyAuditDeadLetterWebhookVerificationException ex) {
            response.setStatus(status(ex.reason()));
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"" + ex.getMessage() + "\"}");
            return false;
        }
    }

    private Map<String, String> headers(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return Collections.emptyMap();
        }
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return headers;
    }

    private int status(PrivacyAuditDeadLetterWebhookVerificationException.Reason reason) {
        return switch (reason) {
            case INVALID_TIMESTAMP -> HttpServletResponse.SC_BAD_REQUEST;
            case REPLAY_DETECTED -> HttpServletResponse.SC_CONFLICT;
            case INVALID_AUTHORIZATION, MISSING_SIGNATURE_HEADERS, EXPIRED_TIMESTAMP, INVALID_SIGNATURE -> HttpServletResponse.SC_UNAUTHORIZED;
        };
    }
}
