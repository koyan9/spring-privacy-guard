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

public class JdbcPrivacyAuditRepository implements PrivacyAuditRepository, PrivacyAuditQueryRepository, PrivacyAuditStatsRepository {

    private static final TypeReference<Map<String, String>> DETAILS_TYPE = new TypeReference<>() {
    };

    private final JdbcOperations jdbcOperations;
    private final ObjectMapper objectMapper;
    private final String tableName;

    public JdbcPrivacyAuditRepository(JdbcOperations jdbcOperations, ObjectMapper objectMapper, String tableName) {
        this.jdbcOperations = jdbcOperations;
        this.objectMapper = objectMapper;
        this.tableName = tableName;
    }

    @Override
    public void save(PrivacyAuditEvent event) {
        jdbcOperations.update(
                "insert into " + tableName + " (occurred_at, action, resource_type, resource_id, actor, outcome, details_json) values (?, ?, ?, ?, ?, ?, ?)",
                event.occurredAt(),
                event.action(),
                event.resourceType(),
                event.resourceId(),
                event.actor(),
                event.outcome(),
                toJson(event)
        );
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
    public PrivacyAuditQueryStats computeStats(PrivacyAuditQueryCriteria criteria) {
        QueryParts queryParts = buildWhereClause(criteria.normalize());
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
        return new QueryParts(sql.toString(), args);
    }

    private void appendEquals(StringBuilder sql, List<Object> args, String columnName, String value) {
        if (value == null) {
            return;
        }
        sql.append(" and ").append(columnName).append(" = ?");
        args.add(value);
    }

    private void appendLike(StringBuilder sql, List<Object> args, String columnName, String value) {
        if (value == null) {
            return;
        }
        sql.append(" and ").append(columnName).append(" like ?");
        args.add("%" + value + "%");
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

    private record QueryParts(String whereClause, List<Object> args) {
    }
}