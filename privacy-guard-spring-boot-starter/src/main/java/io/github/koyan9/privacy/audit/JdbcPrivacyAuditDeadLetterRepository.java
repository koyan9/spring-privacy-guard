/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcOperations;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JdbcPrivacyAuditDeadLetterRepository implements
        PrivacyAuditDeadLetterRepository,
        PrivacyAuditDeadLetterStatsRepository,
        PrivacyTenantAuditDeadLetterReadRepository,
        PrivacyTenantAuditDeadLetterWriteRepository,
        PrivacyTenantAuditDeadLetterDeleteRepository,
        PrivacyTenantAuditDeadLetterReplayRepository {

    private static final TypeReference<Map<String, String>> DETAILS_TYPE = new TypeReference<>() {
    };

    private final JdbcOperations jdbcOperations;
    private final ObjectMapper objectMapper;
    private final String tableName;
    private final String tenantColumnName;
    private final String tenantDetailKey;

    public JdbcPrivacyAuditDeadLetterRepository(JdbcOperations jdbcOperations, ObjectMapper objectMapper, String tableName) {
        this(jdbcOperations, objectMapper, tableName, null, "tenantId");
    }

    public JdbcPrivacyAuditDeadLetterRepository(
            JdbcOperations jdbcOperations,
            ObjectMapper objectMapper,
            String tableName,
            String tenantColumnName,
            String tenantDetailKey
    ) {
        this.jdbcOperations = jdbcOperations;
        this.objectMapper = objectMapper;
        this.tableName = tableName;
        this.tenantColumnName = normalizeColumnName(tenantColumnName);
        this.tenantDetailKey = normalizeTenantDetailKey(tenantDetailKey);
    }

    @Override
    public void save(PrivacyAuditDeadLetterEntry entry) {
        jdbcOperations.update(insertSql(), toSqlArgs(entry));
    }

    @Override
    public void save(PrivacyTenantAuditDeadLetterWriteRequest request) {
        jdbcOperations.update(insertSql(), toSqlArgs(request));
    }

    @Override
    public void saveAll(List<PrivacyAuditDeadLetterEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        jdbcOperations.batchUpdate(insertSql(), entries.stream().map(this::toSqlArgs).toList());
    }

    @Override
    public void saveAllTenantAware(List<PrivacyTenantAuditDeadLetterWriteRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        jdbcOperations.batchUpdate(insertSql(), requests.stream().map(this::toSqlArgs).toList());
    }

    @Override
    public List<PrivacyAuditDeadLetterEntry> findAll() {
        return jdbcOperations.query(
                "select id, failed_at, attempts, error_type, error_message, occurred_at, action, resource_type, resource_id, actor, outcome, details_json from " + tableName + " order by failed_at desc, id desc",
                (resultSet, rowNum) -> mapEntry(resultSet)
        );
    }

    @Override
    public List<PrivacyAuditDeadLetterEntry> findByCriteria(PrivacyAuditDeadLetterQueryCriteria criteria) {
        PrivacyAuditDeadLetterQueryCriteria normalized = criteria == null
                ? PrivacyAuditDeadLetterQueryCriteria.recent(100)
                : criteria.normalize();
        QueryParts queryParts = buildWhereClause(normalized);
        String sql = "select id, failed_at, attempts, error_type, error_message, occurred_at, action, resource_type, resource_id, actor, outcome, details_json from "
                + tableName
                + queryParts.whereClause()
                + " order by failed_at " + normalized.sortDirection().name() + ", id " + normalized.sortDirection().name()
                + " limit ? offset ?";
        queryParts.args().add(normalized.limit());
        queryParts.args().add(normalized.offset());
        return jdbcOperations.query(sql, (resultSet, rowNum) -> mapEntry(resultSet), queryParts.args().toArray());
    }

    @Override
    public List<PrivacyAuditDeadLetterEntry> findByCriteria(
            String tenantId,
            String tenantDetailKey,
            PrivacyAuditDeadLetterQueryCriteria criteria
    ) {
        PrivacyAuditDeadLetterQueryCriteria normalized = criteria == null
                ? PrivacyAuditDeadLetterQueryCriteria.recent(100)
                : criteria.normalize();
        QueryParts queryParts = buildWhereClause(normalized, tenantId, tenantDetailKey);
        String sql = "select id, failed_at, attempts, error_type, error_message, occurred_at, action, resource_type, resource_id, actor, outcome, details_json from "
                + tableName
                + queryParts.whereClause()
                + " order by failed_at " + normalized.sortDirection().name() + ", id " + normalized.sortDirection().name()
                + " limit ? offset ?";
        queryParts.args().add(normalized.limit());
        queryParts.args().add(normalized.offset());
        return jdbcOperations.query(sql, (resultSet, rowNum) -> mapEntry(resultSet), queryParts.args().toArray());
    }

    @Override
    public PrivacyAuditDeadLetterStats computeStats(PrivacyAuditDeadLetterQueryCriteria criteria) {
        PrivacyAuditDeadLetterQueryCriteria normalized = criteria == null
                ? PrivacyAuditDeadLetterQueryCriteria.recent(100)
                : criteria.normalize();
        QueryParts queryParts = buildWhereClause(normalized);
        long total = queryForCount("select count(*) from " + tableName + queryParts.whereClause(), queryParts.args());
        Map<String, Long> byAction = queryForGroupedCount("action", queryParts);
        Map<String, Long> byOutcome = queryForGroupedCount("outcome", queryParts);
        Map<String, Long> byResourceType = queryForGroupedCount("resource_type", queryParts);
        Map<String, Long> byErrorType = queryForGroupedCount("error_type", queryParts);
        return new PrivacyAuditDeadLetterStats(total, byAction, byOutcome, byResourceType, byErrorType);
    }

    @Override
    public PrivacyAuditDeadLetterStats computeStats(
            String tenantId,
            String tenantDetailKey,
            PrivacyAuditDeadLetterQueryCriteria criteria
    ) {
        PrivacyAuditDeadLetterQueryCriteria normalized = criteria == null
                ? PrivacyAuditDeadLetterQueryCriteria.recent(100)
                : criteria.normalize();
        QueryParts queryParts = buildWhereClause(normalized, tenantId, tenantDetailKey);
        long total = queryForCount("select count(*) from " + tableName + queryParts.whereClause(), queryParts.args());
        Map<String, Long> byAction = queryForGroupedCount("action", queryParts);
        Map<String, Long> byOutcome = queryForGroupedCount("outcome", queryParts);
        Map<String, Long> byResourceType = queryForGroupedCount("resource_type", queryParts);
        Map<String, Long> byErrorType = queryForGroupedCount("error_type", queryParts);
        return new PrivacyAuditDeadLetterStats(total, byAction, byOutcome, byResourceType, byErrorType);
    }

    @Override
    public boolean supportsTenantRead() {
        return true;
    }

    @Override
    public Optional<PrivacyAuditDeadLetterEntry> findById(
            String tenantId,
            String tenantDetailKey,
            long id
    ) {
        if (tenantId == null || tenantId.isBlank() || tenantDetailKey == null || tenantDetailKey.isBlank()) {
            return findById(id);
        }
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "select id, failed_at, attempts, error_type, error_message, occurred_at, action, resource_type, resource_id, actor, outcome, details_json from "
                        + tableName
                        + " where id = ?"
        );
        args.add(id);
        appendTenantDetailContains(sql, args, tenantId, tenantDetailKey);
        List<PrivacyAuditDeadLetterEntry> results = jdbcOperations.query(
                sql.toString(),
                (resultSet, rowNum) -> mapEntry(resultSet),
                args.toArray()
        );
        return results.stream().findFirst();
    }

    @Override
    public boolean supportsTenantFindById() {
        return true;
    }

    @Override
    public boolean supportsTenantExchangeRead() {
        return true;
    }

    @Override
    public boolean supportsTenantImport() {
        return true;
    }

    @Override
    public boolean supportsTenantWrite() {
        return true;
    }

    @Override
    public Optional<PrivacyAuditDeadLetterEntry> findById(long id) {
        List<PrivacyAuditDeadLetterEntry> results = jdbcOperations.query(
                "select id, failed_at, attempts, error_type, error_message, occurred_at, action, resource_type, resource_id, actor, outcome, details_json from " + tableName + " where id = ?",
                (resultSet, rowNum) -> mapEntry(resultSet),
                id
        );
        return results.stream().findFirst();
    }

    @Override
    public boolean deleteById(long id) {
        Integer updated = jdbcOperations.update("delete from " + tableName + " where id = ?", id);
        return updated != null && updated > 0;
    }

    @Override
    public boolean deleteById(String tenantId, String tenantDetailKey, long id) {
        if (tenantId == null || tenantId.isBlank() || tenantDetailKey == null || tenantDetailKey.isBlank()) {
            return deleteById(id);
        }
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("delete from " + tableName + " where id = ?");
        args.add(id);
        appendTenantDetailContains(sql, args, tenantId, tenantDetailKey);
        Integer updated = jdbcOperations.update(sql.toString(), args.toArray());
        return updated != null && updated > 0;
    }

    @Override
    public int deleteByCriteria(
            String tenantId,
            String tenantDetailKey,
            PrivacyAuditDeadLetterQueryCriteria criteria
    ) {
        PrivacyAuditDeadLetterQueryCriteria normalized = criteria == null
                ? PrivacyAuditDeadLetterQueryCriteria.recent(100)
                : criteria.normalize();
        QueryParts queryParts = buildWhereClause(normalized, tenantId, tenantDetailKey);
        String selectSql = "select id from " + tableName
                + queryParts.whereClause()
                + " order by failed_at " + normalized.sortDirection().name() + ", id " + normalized.sortDirection().name()
                + " limit ? offset ?";
        queryParts.args().add(normalized.limit());
        queryParts.args().add(normalized.offset());
        String sql = "delete from " + tableName + " where id in (select id from (" + selectSql + ") tenant_scope)";
        Integer updated = jdbcOperations.update(sql, queryParts.args().toArray());
        return updated == null ? 0 : updated;
    }

    @Override
    public boolean supportsTenantDelete() {
        return true;
    }

    @Override
    public boolean supportsTenantDeleteById() {
        return true;
    }

    @Override
    public PrivacyAuditDeadLetterReplayResult replayByCriteria(
            String tenantId,
            String tenantDetailKey,
            PrivacyAuditDeadLetterQueryCriteria criteria,
            java.util.function.Predicate<PrivacyAuditDeadLetterEntry> replayAction
    ) {
        List<PrivacyAuditDeadLetterEntry> selected = findByCriteria(tenantId, tenantDetailKey, criteria);
        List<Long> replayedIds = new ArrayList<>();
        List<Long> failedIds = new ArrayList<>();
        for (PrivacyAuditDeadLetterEntry entry : selected) {
            if (entry.id() == null) {
                continue;
            }
            if (!replayAction.test(entry)) {
                failedIds.add(entry.id());
                continue;
            }
            if (deleteById(tenantId, tenantDetailKey, entry.id())) {
                replayedIds.add(entry.id());
            }
        }
        return new PrivacyAuditDeadLetterReplayResult(
                selected.size(),
                replayedIds.size(),
                failedIds.size(),
                List.copyOf(replayedIds),
                List.copyOf(failedIds)
        );
    }

    @Override
    public boolean supportsTenantReplay() {
        return true;
    }

    @Override
    public boolean replayById(
            String tenantId,
            String tenantDetailKey,
            long id,
            java.util.function.Predicate<PrivacyAuditDeadLetterEntry> replayAction
    ) {
        return findById(tenantId, tenantDetailKey, id)
                .filter(entry -> entry.id() != null)
                .map(entry -> replayAction.test(entry)
                        && deleteById(tenantId, tenantDetailKey, entry.id()))
                .orElse(false);
    }

    @Override
    public boolean supportsTenantReplayById() {
        return true;
    }

    private Map<String, Long> queryForGroupedCount(String column, QueryParts queryParts) {
        String sql = "select " + column + ", count(*) as cnt from " + tableName + queryParts.whereClause() + " group by " + column;
        return jdbcOperations.query(sql, resultSet -> {
            Map<String, Long> counts = new LinkedHashMap<>();
            while (resultSet.next()) {
                counts.put(resultSet.getString(1), resultSet.getLong(2));
            }
            return counts;
        }, queryParts.args().toArray());
    }

    private long queryForCount(String sql, List<Object> args) {
        Long count = jdbcOperations.queryForObject(sql, Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    private QueryParts buildWhereClause(PrivacyAuditDeadLetterQueryCriteria criteria) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(" where 1=1");

        appendEquals(sql, args, "action", criteria.action());
        appendLike(sql, args, "action", criteria.actionLike());
        appendEquals(sql, args, "resource_type", criteria.resourceType());
        appendLike(sql, args, "resource_type", criteria.resourceTypeLike());
        appendEquals(sql, args, "resource_id", criteria.resourceId());
        appendLike(sql, args, "resource_id", criteria.resourceIdLike());
        appendEquals(sql, args, "actor", criteria.actor());
        appendLike(sql, args, "actor", criteria.actorLike());
        appendEquals(sql, args, "outcome", criteria.outcome());
        appendLike(sql, args, "outcome", criteria.outcomeLike());
        appendEquals(sql, args, "error_type", criteria.errorType());
        appendLike(sql, args, "error_message", criteria.errorMessageLike());
        appendFrom(sql, args, "failed_at", criteria.failedFrom());
        appendTo(sql, args, "failed_at", criteria.failedTo());
        appendFrom(sql, args, "occurred_at", criteria.occurredFrom());
        appendTo(sql, args, "occurred_at", criteria.occurredTo());
        return new QueryParts(sql, args);
    }

    private QueryParts buildWhereClause(
            PrivacyAuditDeadLetterQueryCriteria criteria,
            String tenantId,
            String tenantDetailKey
    ) {
        QueryParts queryParts = buildWhereClause(criteria);
        appendTenantDetailContains(queryParts.whereClauseBuilder(), queryParts.args(), tenantId, tenantDetailKey);
        return queryParts;
    }

    private void appendEquals(StringBuilder sql, List<Object> args, String column, String value) {
        if (value != null && !value.isBlank()) {
            sql.append(" and ").append(column).append(" = ?");
            args.add(value);
        }
    }

    private void appendLike(StringBuilder sql, List<Object> args, String column, String value) {
        if (value != null && !value.isBlank()) {
            sql.append(" and ").append(column).append(" like ?");
            args.add("%" + value + "%");
        }
    }

    private void appendTenantDetailContains(StringBuilder sql, List<Object> args, String tenantId, String tenantDetailKey) {
        if (tenantId == null || tenantId.isBlank() || tenantDetailKey == null || tenantDetailKey.isBlank()) {
            return;
        }
        if (hasTenantColumn()) {
            sql.append(" and ").append(tenantColumnName).append(" = ?");
            args.add(tenantId);
            return;
        }
        sql.append(" and details_json like ? escape '\\'");
        args.add("%" + escapeLike(jsonDetailFragment(tenantDetailKey, tenantId)) + "%");
    }

    private void appendFrom(StringBuilder sql, List<Object> args, String column, Instant value) {
        if (value != null) {
            sql.append(" and ").append(column).append(" >= ?");
            args.add(value);
        }
    }

    private void appendTo(StringBuilder sql, List<Object> args, String column, Instant value) {
        if (value != null) {
            sql.append(" and ").append(column).append(" <= ?");
            args.add(value);
        }
    }

    private String insertSql() {
        if (hasTenantColumn()) {
            return "insert into " + tableName + " (failed_at, attempts, error_type, error_message, occurred_at, action, resource_type, resource_id, actor, outcome, details_json, " + tenantColumnName + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        }
        return "insert into " + tableName + " (failed_at, attempts, error_type, error_message, occurred_at, action, resource_type, resource_id, actor, outcome, details_json) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    private Object[] toSqlArgs(PrivacyAuditDeadLetterEntry entry) {
        return toSqlArgs(new PrivacyTenantAuditDeadLetterWriteRequest(entry, null, tenantDetailKey));
    }

    private Object[] toSqlArgs(PrivacyTenantAuditDeadLetterWriteRequest request) {
        PrivacyAuditDeadLetterEntry entry = materializeTenantDetails(request);
        if (hasTenantColumn()) {
            return new Object[]{
                    entry.failedAt(),
                    entry.attempts(),
                    entry.errorType(),
                    entry.errorMessage(),
                    entry.occurredAt(),
                    entry.action(),
                    entry.resourceType(),
                    entry.resourceId(),
                    entry.actor(),
                    entry.outcome(),
                    toJson(entry),
                    resolveTenantColumnValue(entry.details(), request.tenantId(), request.tenantDetailKey())
            };
        }
        return new Object[]{
                entry.failedAt(),
                entry.attempts(),
                entry.errorType(),
                entry.errorMessage(),
                entry.occurredAt(),
                entry.action(),
                entry.resourceType(),
                entry.resourceId(),
                entry.actor(),
                entry.outcome(),
                toJson(entry)
        };
    }

    private PrivacyAuditDeadLetterEntry materializeTenantDetails(PrivacyTenantAuditDeadLetterWriteRequest request) {
        PrivacyAuditDeadLetterEntry entry = request.entry();
        String tenantId = request.tenantId();
        String detailKey = normalizeTenantDetailKey(request.tenantDetailKey());
        if (tenantId == null || tenantId.isBlank()) {
            return entry;
        }
        if (tenantId.equals(entry.details().get(detailKey))) {
            return entry;
        }
        Map<String, String> details = new LinkedHashMap<>(entry.details());
        details.putIfAbsent(detailKey, tenantId);
        return new PrivacyAuditDeadLetterEntry(
                entry.id(),
                entry.failedAt(),
                entry.attempts(),
                entry.errorType(),
                entry.errorMessage(),
                entry.occurredAt(),
                entry.action(),
                entry.resourceType(),
                entry.resourceId(),
                entry.actor(),
                entry.outcome(),
                details
        );
    }

    private PrivacyAuditDeadLetterEntry mapEntry(ResultSet resultSet) throws SQLException {
        long id = resultSet.getLong("id");
        Long resolvedId = resultSet.wasNull() ? null : id;
        return new PrivacyAuditDeadLetterEntry(
                resolvedId,
                toInstant(resultSet.getTimestamp("failed_at")),
                resultSet.getInt("attempts"),
                resultSet.getString("error_type"),
                resultSet.getString("error_message"),
                toInstant(resultSet.getTimestamp("occurred_at")),
                resultSet.getString("action"),
                resultSet.getString("resource_type"),
                resultSet.getString("resource_id"),
                resultSet.getString("actor"),
                resultSet.getString("outcome"),
                fromJson(resultSet.getString("details_json"))
        );
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }

    private String toJson(PrivacyAuditDeadLetterEntry entry) {
        try {
            return objectMapper.writeValueAsString(entry.details());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize privacy audit dead letter details", exception);
        }
    }

    private Map<String, String> fromJson(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, DETAILS_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize privacy audit dead letter details", exception);
        }
    }

    private String jsonDetailFragment(String key, String value) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(key, value));
            return json.substring(1, json.length() - 1);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize privacy audit dead-letter tenant details", exception);
        }
    }

    private String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private boolean hasTenantColumn() {
        return tenantColumnName != null;
    }

    private String resolveTenantColumnValue(Map<String, String> details, String requestTenantId, String requestTenantDetailKey) {
        if (!hasTenantColumn()) {
            return null;
        }
        String detailKey = normalizeTenantDetailKey(requestTenantDetailKey);
        String tenantFromDetails = details == null || details.isEmpty() ? null : details.get(detailKey);
        if (tenantFromDetails != null && !tenantFromDetails.isBlank()) {
            return tenantFromDetails;
        }
        if (requestTenantId != null && !requestTenantId.isBlank()) {
            return requestTenantId;
        }
        if (details == null || details.isEmpty()) {
            return null;
        }
        return details.get(tenantDetailKey);
    }

    private String normalizeColumnName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeTenantDetailKey(String value) {
        if (value == null || value.isBlank()) {
            return "tenantId";
        }
        return value.trim();
    }

    private record QueryParts(StringBuilder whereClauseBuilder, List<Object> args) {
        private String whereClause() {
            return whereClauseBuilder.toString();
        }
    }
}
