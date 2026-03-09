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

public class JdbcPrivacyAuditDeadLetterRepository implements PrivacyAuditDeadLetterRepository, PrivacyAuditDeadLetterStatsRepository {

    private static final TypeReference<Map<String, String>> DETAILS_TYPE = new TypeReference<>() {
    };

    private final JdbcOperations jdbcOperations;
    private final ObjectMapper objectMapper;
    private final String tableName;

    public JdbcPrivacyAuditDeadLetterRepository(JdbcOperations jdbcOperations, ObjectMapper objectMapper, String tableName) {
        this.jdbcOperations = jdbcOperations;
        this.objectMapper = objectMapper;
        this.tableName = tableName;
    }

    @Override
    public void save(PrivacyAuditDeadLetterEntry entry) {
        jdbcOperations.update(insertSql(), toSqlArgs(entry));
    }

    @Override
    public void saveAll(List<PrivacyAuditDeadLetterEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        jdbcOperations.batchUpdate(insertSql(), entries.stream().map(this::toSqlArgs).toList());
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
        return new QueryParts(sql.toString(), args);
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
        return "insert into " + tableName + " (failed_at, attempts, error_type, error_message, occurred_at, action, resource_type, resource_id, actor, outcome, details_json) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    private Object[] toSqlArgs(PrivacyAuditDeadLetterEntry entry) {
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

    private record QueryParts(String whereClause, List<Object> args) {
    }
}
