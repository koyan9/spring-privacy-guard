/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcOperations;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class JdbcPrivacyAuditDeadLetterWebhookReplayStore implements PrivacyAuditDeadLetterWebhookReplayStore {

    private final JdbcOperations jdbcOperations;
    private final String tableName;

    public JdbcPrivacyAuditDeadLetterWebhookReplayStore(JdbcOperations jdbcOperations, String tableName) {
        this.jdbcOperations = jdbcOperations;
        this.tableName = tableName;
    }

    @Override
    public boolean markIfNew(String nonce, Instant now, java.time.Duration ttl) {
        cleanup(now);
        Instant expiresAt = now.plus(ttl);
        try {
            Integer updated = jdbcOperations.update(insertSql(), nonce, Timestamp.from(expiresAt));
            return updated != null && updated > 0;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public Map<String, Instant> snapshot() {
        return jdbcOperations.query(selectSql(), resultSet -> {
            Map<String, Instant> snapshot = new LinkedHashMap<>();
            while (resultSet.next()) {
                snapshot.put(resultSet.getString("nonce"), toInstant(resultSet.getTimestamp("expires_at")));
            }
            return Map.copyOf(snapshot);
        });
    }

    @Override
    public void clear() {
        jdbcOperations.update(clearSql());
    }

    private void cleanup(Instant now) {
        jdbcOperations.update(cleanupSql(), Timestamp.from(now));
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }

    private String insertSql() {
        return "insert into " + tableName + " (nonce, expires_at) values (?, ?)";
    }

    private String selectSql() {
        return "select nonce, expires_at from " + tableName + " order by expires_at asc, nonce asc";
    }

    private String cleanupSql() {
        return "delete from " + tableName + " where expires_at < ?";
    }

    private String clearSql() {
        return "delete from " + tableName;
    }
}
