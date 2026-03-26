/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.springframework.util.AntPathMatcher;

import java.util.List;

public class PrivacyAuditDeadLetterWebhookVerificationRouteRegistry {

    private final List<Route> routes;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public PrivacyAuditDeadLetterWebhookVerificationRouteRegistry(List<Route> routes) {
        this.routes = routes == null ? List.of() : List.copyOf(routes);
    }

    public boolean hasRoutes() {
        return !routes.isEmpty();
    }

    public boolean matches(String requestUri) {
        return find(requestUri) != null;
    }

    public Route find(String requestUri) {
        if (requestUri == null || requestUri.isBlank()) {
            return null;
        }
        for (Route route : routes) {
            if (pathMatcher.match(route.pathPattern(), requestUri)) {
                return route;
            }
        }
        return null;
    }

    public List<String> pathPatterns() {
        return routes.stream().map(Route::pathPattern).toList();
    }

    public record Route(String tenantId, String pathPattern, PrivacyAuditDeadLetterWebhookRequestVerifier verifier) {
    }
}
