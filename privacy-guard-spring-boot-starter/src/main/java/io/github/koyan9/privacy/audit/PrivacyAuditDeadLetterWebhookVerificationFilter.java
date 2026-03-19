/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

public class PrivacyAuditDeadLetterWebhookVerificationFilter extends OncePerRequestFilter {

    private final PrivacyAuditDeadLetterWebhookRequestVerifier verifier;
    private final String pathPattern;
    private final PrivacyAuditDeadLetterWebhookVerificationTelemetry telemetry;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public PrivacyAuditDeadLetterWebhookVerificationFilter(
            PrivacyAuditDeadLetterWebhookRequestVerifier verifier,
            String pathPattern
    ) {
        this(verifier, pathPattern, PrivacyAuditDeadLetterWebhookVerificationTelemetry.noop());
    }

    public PrivacyAuditDeadLetterWebhookVerificationFilter(
            PrivacyAuditDeadLetterWebhookRequestVerifier verifier,
            String pathPattern,
            PrivacyAuditDeadLetterWebhookVerificationTelemetry telemetry
    ) {
        this.verifier = verifier;
        this.pathPattern = pathPattern;
        this.telemetry = telemetry == null ? PrivacyAuditDeadLetterWebhookVerificationTelemetry.noop() : telemetry;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !pathMatcher.match(pathPattern, request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request, body);
        try {
            verifier.verify(headers(request), new String(body, StandardCharsets.UTF_8));
        } catch (PrivacyAuditDeadLetterWebhookVerificationException ex) {
            telemetry.recordFailure(ex.reason());
            response.setStatus(status(ex.reason()));
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"" + ex.getMessage() + "\",\"reason\":\"" + ex.reasonCode() + "\"}");
            return;
        }
        filterChain.doFilter(wrappedRequest, response);
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
