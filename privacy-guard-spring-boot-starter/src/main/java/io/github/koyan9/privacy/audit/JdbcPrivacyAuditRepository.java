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
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JdbcPrivacyAuditRepository implements PrivacyAuditRepository, PrivacyAuditQueryRepository, PrivacyAuditStatsRepository, PrivacyTenantAuditReadRepository, PrivacyTenantAuditWriteRepository {

    private static final TypeReference<Map<String, String>> DETAILS_TYPE = new TypeReference<>() {
    };

    private final JdbcOperations jdbcOperations;
    private final ObjectMapper objectMapper;
    private final String tableName;
    private final String tenantColumnName;
    private final String tenantDetailKey;

    public JdbcPrivacyAuditRepository(JdbcOperations jdbcOperations, ObjectMapper objectMapper, String tableName) {
        this(jdbcOperations, objectMapper, tableName, null, "tenantId");
    }

    public JdbcPrivacyAuditRepository(
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
    public void save(PrivacyAuditEvent event) {
        jdbcOperations.update(
                insertSql(),
                toSqlArgs(event)
        );
    }

    @Override
    public void save(PrivacyTenantAuditWriteRequest request) {
        jdbcOperations.update(insertSql(), toSqlArgs(request));
    }

    @Override
    public void saveAll(List<PrivacyAuditEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        List<Object[]> batchArgs = events.stream()
                .map(this::toSqlArgs)
                .toList();
        jdbcOperations.batchUpdate(insertSql(), batchArgs);
    }

    @Override
    public void saveAllTenantAware(List<PrivacyTenantAuditWriteRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        List<Object[]> batchArgs = requests.stream()
                .map(this::toSqlArgs)
                .toList();
        jdbcOperations.batchUpdate(insertSql(), batchArgs);
    }

    @Override
    public boolean supportsTenantRead() {
        return true;
    }

    @Override
    public boolean supportsTenantWrite() {
        return true;
    }

    @Override
    public List<PrivacyAuditEvent> findByCriteria(PrivacyAuditQueryCriteria criteria) {
        QueryParts queryParts = buildWhereClause(criteria.normalize());
        String sql = "select occurred_at, action, resource_type, resource_id, actor, outcome, details_json from "
                + tableName
                + queryParts.whereClause()
                + " order by occurred_at " + criteria.normalize().sortDirection().name()
                + " limit ? offset ?";
        queryParts.args().add(criteria.normalize().limit());
        queryParts.args().add(criteria.normalize().offset());
        return jdbcOperations.query(sql, (resultSet, rowNum) -> mapEvent(resultSet), queryParts.args().toArray());
    }

    @Override
    public List<PrivacyAuditEvent> findByCriteria(String tenantId, String tenantDetailKey, PrivacyAuditQueryCriteria criteria) {
        PrivacyAuditQueryCriteria normalized = criteria.normalize();
        QueryParts queryParts = buildWhereClause(normalized, tenantId, tenantDetailKey);
        String sql = "select occurred_at, action, resource_type, resource_id, actor, outcome, details_json from "
                + tableName
                + queryParts.whereClause()
                + " order by occurred_at " + normalized.sortDirection().name()
                + " limit ? offset ?";
        queryParts.args().add(normalized.limit());
        queryParts.args().add(normalized.offset());
        return jdbcOperations.query(sql, (resultSet, rowNum) -> mapEvent(resultSet), queryParts.args().toArray());
    }

    @Override
    public PrivacyAuditQueryStats computeStats(PrivacyAuditQueryCriteria criteria) {
        QueryParts queryParts = buildWhereClause(criteria.normalize());
        long total = queryForCount("select count(*) from " + tableName + queryParts.whereClause(), queryParts.args());
        Map<String, Long> byAction = queryForGroupedCount("action", queryParts);
        Map<String, Long> byOutcome = queryForGroupedCount("outcome", queryParts);
        Map<String, Long> byResourceType = queryForGroupedCount("resource_type", queryParts);
        return new PrivacyAuditQueryStats(total, byAction, byOutcome, byResourceType);
    }

    @Override
    public PrivacyAuditQueryStats computeStats(String tenantId, String tenantDetailKey, PrivacyAuditQueryCriteria criteria) {
        PrivacyAuditQueryCriteria normalized = criteria.normalize();
        QueryParts queryParts = buildWhereClause(normalized, tenantId, tenantDetailKey);
        long total = queryForCount("select count(*) from " + tableName + queryParts.whereClause(), queryParts.args());
        Map<String, Long> byAction = queryForGroupedCount("action", queryParts);
        Map<String, Long> byOutcome = queryForGroupedCount("outcome", queryParts);
        Map<String, Long> byResourceType = queryForGroupedCount("resource_type", queryParts);
        return new PrivacyAuditQueryStats(total, byAction, byOutcome, byResourceType);
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

    private QueryParts buildWhereClause(PrivacyAuditQueryCriteria normalized) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(" where 1=1");

        appendEquals(sql, args, "action", normalized.action());
        appendLike(sql, args, "action", normalized.actionLike());
        appendEquals(sql, args, "resource_type", normalized.resourceType());
        appendLike(sql, args, "resource_type", normalized.resourceTypeLike());
        appendEquals(sql, args, "resource_id", normalized.resourceId());
        appendLike(sql, args, "resource_id", normalized.resourceIdLike());
        appendEquals(sql, args, "actor", normalized.actor());
        appendLike(sql, args, "actor", normalized.actorLike());
        appendEquals(sql, args, "outcome", normalized.outcome());
        appendLike(sql, args, "outcome", normalized.outcomeLike());
        if (normalized.occurredFrom() != null) {
            sql.append(" and occurred_at >= ?");
            args.add(normalized.occurredFrom());
        }
        if (normalized.occurredTo() != null) {
            sql.append(" and occurred_at <= ?");
            args.add(normalized.occurredTo());
        }
        return new QueryParts(sql, args);
    }

    private QueryParts buildWhereClause(PrivacyAuditQueryCriteria normalized, String tenantId, String tenantDetailKey) {
        QueryParts queryParts = buildWhereClause(normalized);
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

    private PrivacyAuditEvent mapEvent(ResultSet resultSet) throws SQLException {
        return new PrivacyAuditEvent(
                toInstant(resultSet),
                resultSet.getString("action"),
                resultSet.getString("resource_type"),
                resultSet.getString("resource_id"),
                resultSet.getString("actor"),
                resultSet.getString("outcome"),
                fromJson(resultSet.getString("details_json"))
        );
    }

    private Instant toInstant(ResultSet resultSet) throws SQLException {
        java.sql.Timestamp timestamp = resultSet.getTimestamp("occurred_at");
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }

    private String insertSql() {
        if (hasTenantColumn()) {
            return "insert into " + tableName + " (occurred_at, action, resource_type, resource_id, actor, outcome, details_json, " + tenantColumnName + ") values (?, ?, ?, ?, ?, ?, ?, ?)";
        }
        return "insert into " + tableName + " (occurred_at, action, resource_type, resource_id, actor, outcome, details_json) values (?, ?, ?, ?, ?, ?, ?)";
    }

    private Object[] toSqlArgs(PrivacyAuditEvent event) {
        return toSqlArgs(new PrivacyTenantAuditWriteRequest(event, null, tenantDetailKey));
    }

    private Object[] toSqlArgs(PrivacyTenantAuditWriteRequest request) {
        PrivacyAuditEvent event = materializeTenantDetails(request);
        if (hasTenantColumn()) {
            return new Object[]{
                    event.occurredAt(),
                    event.action(),
                    event.resourceType(),
                    event.resourceId(),
                    event.actor(),
                    event.outcome(),
                    toJson(event),
                    resolveTenantColumnValue(event.details(), request.tenantId(), request.tenantDetailKey())
            };
        }
        return new Object[]{
                event.occurredAt(),
                event.action(),
                event.resourceType(),
                event.resourceId(),
                event.actor(),
                event.outcome(),
                toJson(event)
        };
    }

    private PrivacyAuditEvent materializeTenantDetails(PrivacyTenantAuditWriteRequest request) {
        PrivacyAuditEvent event = request.event();
        String tenantId = request.tenantId();
        String detailKey = normalizeTenantDetailKey(request.tenantDetailKey());
        if (tenantId == null || tenantId.isBlank()) {
            return event;
        }
        if (tenantId.equals(event.details().get(detailKey))) {
            return event;
        }
        Map<String, String> details = new LinkedHashMap<>(event.details());
        details.putIfAbsent(detailKey, tenantId);
        return new PrivacyAuditEvent(
                event.occurredAt(),
                event.action(),
                event.resourceType(),
                event.resourceId(),
                event.actor(),
                event.outcome(),
                details
        );
    }

    private String toJson(PrivacyAuditEvent event) {
        try {
            return objectMapper.writeValueAsString(event.details());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize privacy audit details", exception);
        }
    }

    private Map<String, String> fromJson(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, DETAILS_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize privacy audit details", exception);
        }
    }

    private String jsonDetailFragment(String key, String value) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(key, value));
            return json.substring(1, json.length() - 1);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize privacy audit tenant details", exception);
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
