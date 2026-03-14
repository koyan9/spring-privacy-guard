/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcOperations;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class JdbcPrivacyAuditDeadLetterWebhookReplayStore implements PrivacyAuditDeadLetterWebhookReplayStore,
        PrivacyAuditDeadLetterWebhookReplayStoreStatsProvider {

    private final JdbcOperations jdbcOperations;
    private final String tableName;
    private final Duration cleanupInterval;
    private final int cleanupBatchSize;
    private final AtomicLong lastCleanupAt = new AtomicLong(0L);

    public JdbcPrivacyAuditDeadLetterWebhookReplayStore(JdbcOperations jdbcOperations, String tableName) {
        this(jdbcOperations, tableName, Duration.ofMinutes(5));
    }

    public JdbcPrivacyAuditDeadLetterWebhookReplayStore(
            JdbcOperations jdbcOperations,
            String tableName,
            Duration cleanupInterval
    ) {
        this(jdbcOperations, tableName, cleanupInterval, 500);
    }

    public JdbcPrivacyAuditDeadLetterWebhookReplayStore(
            JdbcOperations jdbcOperations,
            String tableName,
            Duration cleanupInterval,
            int cleanupBatchSize
    ) {
        this.jdbcOperations = jdbcOperations;
        this.tableName = tableName;
        this.cleanupInterval = cleanupInterval == null ? Duration.ZERO : cleanupInterval;
        this.cleanupBatchSize = Math.max(0, cleanupBatchSize);
    }

    @Override
    public boolean markIfNew(String nonce, Instant now, java.time.Duration ttl) {
        cleanupExpiredNonce(nonce, now);
        cleanupExpiredEntries(now);
        Instant expiresAt = now.plus(ttl);
        try {
            Integer updated = jdbcOperations.update(insertSql(), nonce, Timestamp.from(expiresAt));
            return updated != null && updated > 0;
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
    }

    @Override
    public Map<String, Instant> snapshot() {
        Instant now = Instant.now();
        return jdbcOperations.query(selectSql(), resultSet -> {
            Map<String, Instant> snapshot = new LinkedHashMap<>();
            while (resultSet.next()) {
                snapshot.put(resultSet.getString("nonce"), toInstant(resultSet.getTimestamp("expires_at")));
            }
            return Map.copyOf(snapshot);
        }, Timestamp.from(now));
    }

    @Override
    public PrivacyAuditDeadLetterWebhookReplayStoreSnapshot snapshotStats(Instant now, java.time.Duration expiringSoonWindow) {
        int count = Math.toIntExact(queryForCount("select count(*) from " + tableName + " where expires_at >= ?", Timestamp.from(now)));
        long expiringSoon = queryForCount(
                "select count(*) from " + tableName + " where expires_at >= ? and expires_at <= ?",
                Timestamp.from(now),
                Timestamp.from(now.plus(expiringSoonWindow))
        );
        Instant earliest = queryForInstant("select min(expires_at) from " + tableName + " where expires_at >= ?", Timestamp.from(now));
        Instant latest = queryForInstant("select max(expires_at) from " + tableName + " where expires_at >= ?", Timestamp.from(now));
        return new PrivacyAuditDeadLetterWebhookReplayStoreSnapshot(
                count,
                expiringSoon,
                earliest,
                latest,
                expiringSoonWindow.toString()
        );
    }

    @Override
    public void clear() {
        jdbcOperations.update(clearSql());
    }

    private void cleanupExpiredNonce(String nonce, Instant now) {
        jdbcOperations.update(deleteExpiredNonceSql(), nonce, Timestamp.from(now));
    }

    private void cleanupExpiredEntries(Instant now) {
        if (cleanupInterval.isZero() || cleanupInterval.isNegative()) {
            cleanupExpiredBatch(now);
            return;
        }
        long nowMillis = now.toEpochMilli();
        long previous = lastCleanupAt.get();
        if (nowMillis - previous < cleanupInterval.toMillis()) {
            return;
        }
        if (!lastCleanupAt.compareAndSet(previous, nowMillis)) {
            return;
        }
        cleanupExpiredBatch(now);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }

    private String insertSql() {
        return "insert into " + tableName + " (nonce, expires_at) values (?, ?)";
    }

    private String selectSql() {
        return "select nonce, expires_at from " + tableName + " where expires_at >= ? order by expires_at asc, nonce asc";
    }

    private String cleanupSql() {
        return "delete from " + tableName + " where expires_at < ?";
    }

    private String deleteExpiredNonceSql() {
        return "delete from " + tableName + " where nonce = ? and expires_at < ?";
    }

    private void cleanupExpiredBatch(Instant now) {
        if (cleanupBatchSize <= 0) {
            jdbcOperations.update(cleanupSql(), Timestamp.from(now));
            return;
        }
        List<String> expiredNonces = jdbcOperations.query(
                selectExpiredNoncesSql(),
                (resultSet, rowNum) -> resultSet.getString(1),
                Timestamp.from(now),
                cleanupBatchSize
        );
        if (expiredNonces.isEmpty()) {
            return;
        }
        String deleteSql = deleteExpiredNoncesSql(expiredNonces.size());
        jdbcOperations.update(deleteSql, expiredNonces.toArray());
    }

    private String clearSql() {
        return "delete from " + tableName;
    }

    private String selectExpiredNoncesSql() {
        return "select nonce from " + tableName + " where expires_at < ? order by expires_at asc limit ?";
    }

    private String deleteExpiredNoncesSql(int size) {
        String placeholders = String.join(", ", java.util.Collections.nCopies(size, "?"));
        return "delete from " + tableName + " where nonce in (" + placeholders + ")";
    }

    private long queryForCount(String sql, Object... args) {
        Long count;
        if (args == null || args.length == 0) {
            count = jdbcOperations.queryForObject(sql, Long.class);
        } else {
            count = jdbcOperations.queryForObject(sql, Long.class, args);
        }
        return count == null ? 0L : count;
    }

    private Instant queryForInstant(String sql) {
        Timestamp timestamp = jdbcOperations.queryForObject(sql, Timestamp.class);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Instant queryForInstant(String sql, Object... args) {
        Timestamp timestamp = jdbcOperations.queryForObject(sql, Timestamp.class, args);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
