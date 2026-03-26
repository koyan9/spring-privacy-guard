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
import java.util.List;

public class PrivacyAuditDeadLetterWebhookBodyCachingFilter extends OncePerRequestFilter {

    private final List<String> pathPatterns;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public PrivacyAuditDeadLetterWebhookBodyCachingFilter(String pathPattern) {
        this(List.of(pathPattern));
    }

    public PrivacyAuditDeadLetterWebhookBodyCachingFilter(List<String> pathPatterns) {
        this.pathPatterns = pathPatterns == null ? List.of() : List.copyOf(pathPatterns);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || pathPatterns.stream().noneMatch(pattern -> pathMatcher.match(pattern, request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
        filterChain.doFilter(new CachedBodyHttpServletRequest(request, body), response);
    }
}
