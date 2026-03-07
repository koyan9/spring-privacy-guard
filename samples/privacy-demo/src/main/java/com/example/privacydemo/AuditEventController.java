/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import io.github.koyan9.privacy.audit.PrivacyAuditEvent;
import io.github.koyan9.privacy.audit.PrivacyAuditQueryCriteria;
import io.github.koyan9.privacy.audit.PrivacyAuditQueryService;
import io.github.koyan9.privacy.audit.PrivacyAuditQueryStats;
import io.github.koyan9.privacy.audit.PrivacyAuditSortDirection;
import io.github.koyan9.privacy.audit.PrivacyAuditStatsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
class AuditEventController {

    private final PrivacyAuditQueryService privacyAuditQueryService;
    private final PrivacyAuditStatsService privacyAuditStatsService;

    AuditEventController(
            PrivacyAuditQueryService privacyAuditQueryService,
            PrivacyAuditStatsService privacyAuditStatsService
    ) {
        this.privacyAuditQueryService = privacyAuditQueryService;
        this.privacyAuditStatsService = privacyAuditStatsService;
    }

    @GetMapping("/audit-events")
    public List<PrivacyAuditEvent> events(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actionLike,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceTypeLike,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String resourceIdLike,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String actorLike,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String outcomeLike,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredTo,
            @RequestParam(defaultValue = "DESC") PrivacyAuditSortDirection sortDirection,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        return privacyAuditQueryService.findByCriteria(
                new PrivacyAuditQueryCriteria(action, actionLike, resourceType, resourceTypeLike, resourceId, resourceIdLike, actor, actorLike, outcome, outcomeLike, occurredFrom, occurredTo, sortDirection, limit, offset)
        );
    }

    @GetMapping("/audit-events/stats")
    public PrivacyAuditQueryStats stats(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actionLike,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceTypeLike,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String resourceIdLike,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String actorLike,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String outcomeLike,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredTo
    ) {
        return privacyAuditStatsService.computeStats(
                new PrivacyAuditQueryCriteria(action, actionLike, resourceType, resourceTypeLike, resourceId, resourceIdLike, actor, actorLike, outcome, outcomeLike, occurredFrom, occurredTo, PrivacyAuditSortDirection.DESC, 100, 0)
        );
    }
}