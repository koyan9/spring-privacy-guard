/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterEntry;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterExchangeService;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterExportManifest;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterImportResult;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterQueryCriteria;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterReplayResult;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterService;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterStats;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterStatsService;
import io.github.koyan9.privacy.audit.PrivacyAuditEvent;
import io.github.koyan9.privacy.audit.PrivacyAuditQueryCriteria;
import io.github.koyan9.privacy.audit.PrivacyAuditQueryService;
import io.github.koyan9.privacy.audit.PrivacyAuditQueryStats;
import io.github.koyan9.privacy.audit.PrivacyAuditService;
import io.github.koyan9.privacy.audit.PrivacyAuditSortDirection;
import io.github.koyan9.privacy.audit.PrivacyAuditStatsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
class AuditEventController {

    private static final String ADMIN_ACTOR = "demo-admin";

    private final PrivacyAuditQueryService privacyAuditQueryService;
    private final PrivacyAuditStatsService privacyAuditStatsService;
    private final PrivacyAuditDeadLetterService privacyAuditDeadLetterService;
    private final PrivacyAuditDeadLetterStatsService privacyAuditDeadLetterStatsService;
    private final PrivacyAuditDeadLetterExchangeService privacyAuditDeadLetterExchangeService;
    private final PrivacyAuditService privacyAuditService;

    AuditEventController(
            PrivacyAuditQueryService privacyAuditQueryService,
            PrivacyAuditStatsService privacyAuditStatsService,
            PrivacyAuditDeadLetterService privacyAuditDeadLetterService,
            PrivacyAuditDeadLetterStatsService privacyAuditDeadLetterStatsService,
            PrivacyAuditDeadLetterExchangeService privacyAuditDeadLetterExchangeService,
            PrivacyAuditService privacyAuditService
    ) {
        this.privacyAuditQueryService = privacyAuditQueryService;
        this.privacyAuditStatsService = privacyAuditStatsService;
        this.privacyAuditDeadLetterService = privacyAuditDeadLetterService;
        this.privacyAuditDeadLetterStatsService = privacyAuditDeadLetterStatsService;
        this.privacyAuditDeadLetterExchangeService = privacyAuditDeadLetterExchangeService;
        this.privacyAuditService = privacyAuditService;
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
        PrivacyAuditQueryCriteria criteria = new PrivacyAuditQueryCriteria(
                action, actionLike,
                resourceType, resourceTypeLike,
                resourceId, resourceIdLike,
                actor, actorLike,
                outcome, outcomeLike,
                occurredFrom, occurredTo,
                sortDirection,
                limit,
                offset
        );
        List<PrivacyAuditEvent> events = privacyAuditQueryService.findByCriteria(criteria);
        recordManagementAction(
                "AUDIT_EVENTS_QUERY",
                "PrivacyAuditEvent",
                "query",
                queryDetails(criteria.normalize())
        );
        return events;
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
        PrivacyAuditQueryCriteria criteria = new PrivacyAuditQueryCriteria(
                action, actionLike,
                resourceType, resourceTypeLike,
                resourceId, resourceIdLike,
                actor, actorLike,
                outcome, outcomeLike,
                occurredFrom, occurredTo,
                PrivacyAuditSortDirection.DESC,
                100,
                0
        );
        PrivacyAuditQueryStats stats = privacyAuditStatsService.computeStats(criteria);
        recordManagementAction(
                "AUDIT_EVENTS_STATS_QUERY",
                "PrivacyAuditEvent",
                "stats",
                queryDetails(criteria.normalize())
        );
        return stats;
    }

    @GetMapping("/audit-dead-letters")
    public List<PrivacyAuditDeadLetterEntry> deadLetters(
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
            @RequestParam(required = false) String errorType,
            @RequestParam(required = false) String errorMessageLike,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant failedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant failedTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredTo,
            @RequestParam(defaultValue = "DESC") PrivacyAuditSortDirection sortDirection,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        PrivacyAuditDeadLetterQueryCriteria criteria = buildDeadLetterCriteria(
                action, actionLike, resourceType, resourceTypeLike, resourceId, resourceIdLike,
                actor, actorLike, outcome, outcomeLike, errorType, errorMessageLike,
                failedFrom, failedTo, occurredFrom, occurredTo, sortDirection, limit, offset
        );
        List<PrivacyAuditDeadLetterEntry> entries = privacyAuditDeadLetterService.findByCriteria(criteria);
        recordManagementAction(
                "AUDIT_DEAD_LETTERS_QUERY",
                "PrivacyAuditDeadLetter",
                "query",
                deadLetterQueryDetails(criteria.normalize())
        );
        return entries;
    }

    @GetMapping("/audit-dead-letters/stats")
    public PrivacyAuditDeadLetterStats deadLetterStats(
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
            @RequestParam(required = false) String errorType,
            @RequestParam(required = false) String errorMessageLike,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant failedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant failedTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredTo
    ) {
        PrivacyAuditDeadLetterQueryCriteria criteria = buildDeadLetterCriteria(
                action, actionLike, resourceType, resourceTypeLike, resourceId, resourceIdLike,
                actor, actorLike, outcome, outcomeLike, errorType, errorMessageLike,
                failedFrom, failedTo, occurredFrom, occurredTo, PrivacyAuditSortDirection.DESC, 100, 0
        );
        PrivacyAuditDeadLetterStats stats = privacyAuditDeadLetterStatsService.computeStats(criteria);
        recordManagementAction(
                "AUDIT_DEAD_LETTERS_STATS_QUERY",
                "PrivacyAuditDeadLetter",
                "stats",
                deadLetterQueryDetails(criteria.normalize())
        );
        return stats;
    }

    @GetMapping(value = "/audit-dead-letters/export.manifest", produces = MediaType.APPLICATION_JSON_VALUE)
    public PrivacyAuditDeadLetterExportManifest exportDeadLetterManifest(
            @RequestParam(defaultValue = "json") String format,
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
            @RequestParam(required = false) String errorType,
            @RequestParam(required = false) String errorMessageLike,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant failedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant failedTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredTo,
            @RequestParam(defaultValue = "DESC") PrivacyAuditSortDirection sortDirection,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        PrivacyAuditDeadLetterQueryCriteria criteria = buildDeadLetterCriteria(
                action, actionLike, resourceType, resourceTypeLike, resourceId, resourceIdLike,
                actor, actorLike, outcome, outcomeLike, errorType, errorMessageLike,
                failedFrom, failedTo, occurredFrom, occurredTo, sortDirection, limit, offset
        );
        PrivacyAuditDeadLetterExportManifest manifest = privacyAuditDeadLetterExchangeService.exportManifest(criteria, format);
        recordManagementAction(
                "AUDIT_DEAD_LETTERS_EXPORT_MANIFEST",
                "PrivacyAuditDeadLetter",
                "manifest",
                Map.of(
                        "format", manifest.format(),
                        "total", String.valueOf(manifest.total()),
                        "sha256", manifest.sha256()
                )
        );
        return manifest;
    }

    @GetMapping(value = "/audit-dead-letters/export.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public String exportDeadLettersJson(
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
            @RequestParam(required = false) String errorType,
            @RequestParam(required = false) String errorMessageLike,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant failedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant failedTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredTo,
            @RequestParam(defaultValue = "DESC") PrivacyAuditSortDirection sortDirection,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        PrivacyAuditDeadLetterQueryCriteria criteria = buildDeadLetterCriteria(
                action, actionLike, resourceType, resourceTypeLike, resourceId, resourceIdLike,
                actor, actorLike, outcome, outcomeLike, errorType, errorMessageLike,
                failedFrom, failedTo, occurredFrom, occurredTo, sortDirection, limit, offset
        );
        String content = privacyAuditDeadLetterExchangeService.exportJson(criteria);
        recordManagementAction(
                "AUDIT_DEAD_LETTERS_EXPORT",
                "PrivacyAuditDeadLetter",
                "json",
                Map.of("format", "json", "contentLength", String.valueOf(content.length()))
        );
        return content;
    }

    @GetMapping(value = "/audit-dead-letters/export.csv", produces = "text/csv")
    public String exportDeadLettersCsv(
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
            @RequestParam(required = false) String errorType,
            @RequestParam(required = false) String errorMessageLike,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant failedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant failedTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredTo,
            @RequestParam(defaultValue = "DESC") PrivacyAuditSortDirection sortDirection,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        PrivacyAuditDeadLetterQueryCriteria criteria = buildDeadLetterCriteria(
                action, actionLike, resourceType, resourceTypeLike, resourceId, resourceIdLike,
                actor, actorLike, outcome, outcomeLike, errorType, errorMessageLike,
                failedFrom, failedTo, occurredFrom, occurredTo, sortDirection, limit, offset
        );
        String content = privacyAuditDeadLetterExchangeService.exportCsv(criteria);
        recordManagementAction(
                "AUDIT_DEAD_LETTERS_EXPORT",
                "PrivacyAuditDeadLetter",
                "csv",
                Map.of("format", "csv", "contentLength", String.valueOf(content.length()))
        );
        return content;
    }

    @PostMapping(value = "/audit-dead-letters/import.json", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PrivacyAuditDeadLetterImportResult importDeadLettersJson(
            @RequestBody String content,
            @RequestParam(defaultValue = "true") boolean deduplicate,
            @RequestParam(required = false) String checksum
    ) {
        PrivacyAuditDeadLetterImportResult result = privacyAuditDeadLetterExchangeService.importJson(content, deduplicate, checksum);
        recordManagementAction(
                "AUDIT_DEAD_LETTERS_IMPORT",
                "PrivacyAuditDeadLetter",
                "json",
                Map.of(
                        "format", "json",
                        "received", String.valueOf(result.received()),
                        "imported", String.valueOf(result.imported()),
                        "skippedDuplicates", String.valueOf(result.skippedDuplicates()),
                        "deduplicate", String.valueOf(deduplicate),
                        "checksum", result.sha256()
                )
        );
        return result;
    }

    @PostMapping(value = "/audit-dead-letters/import.csv", consumes = "text/csv")
    public PrivacyAuditDeadLetterImportResult importDeadLettersCsv(
            @RequestBody String content,
            @RequestParam(defaultValue = "true") boolean deduplicate,
            @RequestParam(required = false) String checksum
    ) {
        PrivacyAuditDeadLetterImportResult result = privacyAuditDeadLetterExchangeService.importCsv(content, deduplicate, checksum);
        recordManagementAction(
                "AUDIT_DEAD_LETTERS_IMPORT",
                "PrivacyAuditDeadLetter",
                "csv",
                Map.of(
                        "format", "csv",
                        "received", String.valueOf(result.received()),
                        "imported", String.valueOf(result.imported()),
                        "skippedDuplicates", String.valueOf(result.skippedDuplicates()),
                        "deduplicate", String.valueOf(deduplicate),
                        "checksum", result.sha256()
                )
        );
        return result;
    }

    @DeleteMapping("/audit-dead-letters/{id}")
    public boolean deleteDeadLetter(@PathVariable long id) {
        boolean deleted = privacyAuditDeadLetterService.delete(id);
        recordManagementAction(
                "AUDIT_DEAD_LETTER_DELETE",
                "PrivacyAuditDeadLetter",
                String.valueOf(id),
                Map.of("deleted", String.valueOf(deleted))
        );
        return deleted;
    }

    @DeleteMapping("/audit-dead-letters")
    public int deleteDeadLetters(
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
            @RequestParam(required = false) String errorType,
            @RequestParam(required = false) String errorMessageLike,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant failedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant failedTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredTo,
            @RequestParam(defaultValue = "DESC") PrivacyAuditSortDirection sortDirection,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        PrivacyAuditDeadLetterQueryCriteria criteria = buildDeadLetterCriteria(
                action, actionLike, resourceType, resourceTypeLike, resourceId, resourceIdLike,
                actor, actorLike, outcome, outcomeLike, errorType, errorMessageLike,
                failedFrom, failedTo, occurredFrom, occurredTo, sortDirection, limit, offset
        );
        int deleted = privacyAuditDeadLetterService.deleteByCriteria(criteria);
        Map<String, String> details = deadLetterQueryDetails(criteria.normalize());
        details.put("deleted", String.valueOf(deleted));
        recordManagementAction(
                "AUDIT_DEAD_LETTERS_DELETE",
                "PrivacyAuditDeadLetter",
                "criteria",
                details
        );
        return deleted;
    }

    @PostMapping("/audit-dead-letters/{id}/replay")
    public boolean replayDeadLetter(@PathVariable long id) {
        boolean replayed = privacyAuditDeadLetterService.replay(id);
        recordManagementAction(
                "AUDIT_DEAD_LETTER_REPLAY",
                "PrivacyAuditDeadLetter",
                String.valueOf(id),
                Map.of("replayed", String.valueOf(replayed))
        );
        return replayed;
    }

    @PostMapping("/audit-dead-letters/replay")
    public PrivacyAuditDeadLetterReplayResult replayDeadLetters(
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
            @RequestParam(required = false) String errorType,
            @RequestParam(required = false) String errorMessageLike,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant failedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant failedTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredTo,
            @RequestParam(defaultValue = "DESC") PrivacyAuditSortDirection sortDirection,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        PrivacyAuditDeadLetterQueryCriteria criteria = buildDeadLetterCriteria(
                action, actionLike, resourceType, resourceTypeLike, resourceId, resourceIdLike,
                actor, actorLike, outcome, outcomeLike, errorType, errorMessageLike,
                failedFrom, failedTo, occurredFrom, occurredTo, sortDirection, limit, offset
        );
        PrivacyAuditDeadLetterReplayResult result = privacyAuditDeadLetterService.replayByCriteria(criteria);
        Map<String, String> details = deadLetterQueryDetails(criteria.normalize());
        details.put("requested", String.valueOf(result.requested()));
        details.put("replayed", String.valueOf(result.replayed()));
        details.put("failed", String.valueOf(result.failed()));
        recordManagementAction(
                "AUDIT_DEAD_LETTERS_REPLAY",
                "PrivacyAuditDeadLetter",
                "criteria",
                details
        );
        return result;
    }

    private PrivacyAuditDeadLetterQueryCriteria buildDeadLetterCriteria(
            String action,
            String actionLike,
            String resourceType,
            String resourceTypeLike,
            String resourceId,
            String resourceIdLike,
            String actor,
            String actorLike,
            String outcome,
            String outcomeLike,
            String errorType,
            String errorMessageLike,
            Instant failedFrom,
            Instant failedTo,
            Instant occurredFrom,
            Instant occurredTo,
            PrivacyAuditSortDirection sortDirection,
            int limit,
            int offset
    ) {
        return new PrivacyAuditDeadLetterQueryCriteria(
                action, actionLike,
                resourceType, resourceTypeLike,
                resourceId, resourceIdLike,
                actor, actorLike,
                outcome, outcomeLike,
                errorType, errorMessageLike,
                failedFrom, failedTo,
                occurredFrom, occurredTo,
                sortDirection,
                limit,
                offset
        );
    }

    private Map<String, String> queryDetails(PrivacyAuditQueryCriteria criteria) {
        Map<String, String> details = new LinkedHashMap<>();
        putIfPresent(details, "action", criteria.action());
        putIfPresent(details, "actionLike", criteria.actionLike());
        putIfPresent(details, "resourceType", criteria.resourceType());
        putIfPresent(details, "resourceTypeLike", criteria.resourceTypeLike());
        putIfPresent(details, "resourceId", criteria.resourceId());
        putIfPresent(details, "resourceIdLike", criteria.resourceIdLike());
        putIfPresent(details, "actor", criteria.actor());
        putIfPresent(details, "actorLike", criteria.actorLike());
        putIfPresent(details, "outcome", criteria.outcome());
        putIfPresent(details, "outcomeLike", criteria.outcomeLike());
        putIfPresent(details, "occurredFrom", criteria.occurredFrom());
        putIfPresent(details, "occurredTo", criteria.occurredTo());
        details.put("sortDirection", criteria.sortDirection().name());
        details.put("limit", String.valueOf(criteria.limit()));
        details.put("offset", String.valueOf(criteria.offset()));
        return details;
    }

    private Map<String, String> deadLetterQueryDetails(PrivacyAuditDeadLetterQueryCriteria criteria) {
        Map<String, String> details = new LinkedHashMap<>();
        putIfPresent(details, "action", criteria.action());
        putIfPresent(details, "actionLike", criteria.actionLike());
        putIfPresent(details, "resourceType", criteria.resourceType());
        putIfPresent(details, "resourceTypeLike", criteria.resourceTypeLike());
        putIfPresent(details, "resourceId", criteria.resourceId());
        putIfPresent(details, "resourceIdLike", criteria.resourceIdLike());
        putIfPresent(details, "actor", criteria.actor());
        putIfPresent(details, "actorLike", criteria.actorLike());
        putIfPresent(details, "outcome", criteria.outcome());
        putIfPresent(details, "outcomeLike", criteria.outcomeLike());
        putIfPresent(details, "errorType", criteria.errorType());
        putIfPresent(details, "errorMessageLike", criteria.errorMessageLike());
        putIfPresent(details, "failedFrom", criteria.failedFrom());
        putIfPresent(details, "failedTo", criteria.failedTo());
        putIfPresent(details, "occurredFrom", criteria.occurredFrom());
        putIfPresent(details, "occurredTo", criteria.occurredTo());
        details.put("sortDirection", criteria.sortDirection().name());
        details.put("limit", String.valueOf(criteria.limit()));
        details.put("offset", String.valueOf(criteria.offset()));
        return details;
    }

    private void recordManagementAction(String action, String resourceType, String resourceId, Map<String, String> details) {
        privacyAuditService.record(action, resourceType, resourceId, ADMIN_ACTOR, "SUCCESS", details);
    }

    private void putIfPresent(Map<String, String> details, String key, Object value) {
        if (value != null) {
            details.put(key, String.valueOf(value));
        }
    }
}
