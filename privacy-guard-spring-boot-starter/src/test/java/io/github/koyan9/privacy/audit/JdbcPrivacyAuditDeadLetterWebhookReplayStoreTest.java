/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class JdbcPrivacyAuditDeadLetterWebhookReplayStoreTest {

    @Test
    void marksNonceWhenInsertSucceeds() {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        JdbcPrivacyAuditDeadLetterWebhookReplayStore store = new JdbcPrivacyAuditDeadLetterWebhookReplayStore(jdbcOperations, "replay_store");
        Instant now = Instant.now();

        when(jdbcOperations.update(eq("delete from replay_store where nonce = ? and expires_at < ?"), eq("nonce-1"), any(Timestamp.class)))
                .thenReturn(1);
        when(jdbcOperations.update(eq("delete from replay_store where expires_at < ?"), any(Timestamp.class))).thenReturn(1);
        when(jdbcOperations.update(contains("insert into replay_store"), eq("nonce-1"), any(Timestamp.class))).thenReturn(1);

        boolean marked = store.markIfNew("nonce-1", now, Duration.ofMinutes(5));

        assertThat(marked).isTrue();
        verify(jdbcOperations).update(eq("delete from replay_store where nonce = ? and expires_at < ?"), eq("nonce-1"), any(Timestamp.class));
        verify(jdbcOperations).update(eq("delete from replay_store where expires_at < ?"), any(Timestamp.class));
        verify(jdbcOperations).update(contains("insert into replay_store"), eq("nonce-1"), any(Timestamp.class));
    }

    @Test
    void returnsFalseWhenDuplicateKeyDetected() {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        JdbcPrivacyAuditDeadLetterWebhookReplayStore store = new JdbcPrivacyAuditDeadLetterWebhookReplayStore(jdbcOperations, "replay_store");
        Instant now = Instant.now();

        when(jdbcOperations.update(eq("delete from replay_store where nonce = ? and expires_at < ?"), eq("nonce-1"), any(Timestamp.class)))
                .thenReturn(1);
        when(jdbcOperations.update(eq("delete from replay_store where expires_at < ?"), any(Timestamp.class))).thenReturn(1);
        when(jdbcOperations.update(contains("insert into replay_store"), eq("nonce-1"), any(Timestamp.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        boolean marked = store.markIfNew("nonce-1", now, Duration.ofMinutes(5));

        assertThat(marked).isFalse();
    }

    @Test
    void returnsFalseWhenDataIntegrityViolationDetected() {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        JdbcPrivacyAuditDeadLetterWebhookReplayStore store = new JdbcPrivacyAuditDeadLetterWebhookReplayStore(jdbcOperations, "replay_store");
        Instant now = Instant.now();

        when(jdbcOperations.update(eq("delete from replay_store where nonce = ? and expires_at < ?"), eq("nonce-1"), any(Timestamp.class)))
                .thenReturn(1);
        when(jdbcOperations.update(eq("delete from replay_store where expires_at < ?"), any(Timestamp.class))).thenReturn(1);
        when(jdbcOperations.update(contains("insert into replay_store"), eq("nonce-1"), any(Timestamp.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        boolean marked = store.markIfNew("nonce-1", now, Duration.ofMinutes(5));

        assertThat(marked).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void snapshotsReplayStoreThroughJdbcOperations() throws Exception {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        JdbcPrivacyAuditDeadLetterWebhookReplayStore store = new JdbcPrivacyAuditDeadLetterWebhookReplayStore(jdbcOperations, "replay_store");

        when(jdbcOperations.query(eq("select nonce, expires_at from replay_store order by expires_at asc, nonce asc"), any(ResultSetExtractor.class)))
                .thenAnswer(invocation -> {
                    ResultSetExtractor<Map<String, Instant>> extractor = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.next()).thenReturn(true, true, false);
                    when(resultSet.getString("nonce")).thenReturn("nonce-1", "nonce-2");
                    when(resultSet.getTimestamp("expires_at"))
                            .thenReturn(Timestamp.from(Instant.EPOCH), Timestamp.from(Instant.EPOCH.plusSeconds(5)));
                    return extractor.extractData(resultSet);
                });

        Map<String, Instant> snapshot = store.snapshot();

        assertThat(snapshot).containsOnlyKeys("nonce-1", "nonce-2");
        assertThat(snapshot.get("nonce-1")).isEqualTo(Instant.EPOCH);
    }

    @Test
    void clearsReplayStore() {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        JdbcPrivacyAuditDeadLetterWebhookReplayStore store = new JdbcPrivacyAuditDeadLetterWebhookReplayStore(jdbcOperations, "replay_store");

        store.clear();

        verify(jdbcOperations).update("delete from replay_store");
    }

    @Test
    void computesStatsUsingAggregateQueries() {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        JdbcPrivacyAuditDeadLetterWebhookReplayStore store = new JdbcPrivacyAuditDeadLetterWebhookReplayStore(jdbcOperations, "replay_store");
        Instant now = Instant.now();

        when(jdbcOperations.queryForObject("select count(*) from replay_store", Long.class)).thenReturn(4L);
        when(jdbcOperations.queryForObject(contains("where expires_at >= ? and expires_at <= ?"), eq(Long.class), any(Timestamp.class), any(Timestamp.class)))
                .thenReturn(2L);
        when(jdbcOperations.queryForObject("select min(expires_at) from replay_store", Timestamp.class))
                .thenReturn(Timestamp.from(Instant.EPOCH));
        when(jdbcOperations.queryForObject("select max(expires_at) from replay_store", Timestamp.class))
                .thenReturn(Timestamp.from(Instant.EPOCH.plusSeconds(30)));

        PrivacyAuditDeadLetterWebhookReplayStoreSnapshot snapshot = store.snapshotStats(now, Duration.ofSeconds(15));

        assertThat(snapshot.count()).isEqualTo(4);
        assertThat(snapshot.expiringSoon()).isEqualTo(2);
        assertThat(snapshot.earliestExpiry()).isEqualTo(Instant.EPOCH);
        assertThat(snapshot.latestExpiry()).isEqualTo(Instant.EPOCH.plusSeconds(30));
        assertThat(snapshot.expiringSoonWindow()).isEqualTo("PT15S");
    }

    @Test
    void returnsNullExpiriesWhenTableIsEmpty() {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        JdbcPrivacyAuditDeadLetterWebhookReplayStore store = new JdbcPrivacyAuditDeadLetterWebhookReplayStore(jdbcOperations, "replay_store");
        Instant now = Instant.now();

        when(jdbcOperations.queryForObject("select count(*) from replay_store", Long.class)).thenReturn(0L);
        when(jdbcOperations.queryForObject(contains("where expires_at >= ? and expires_at <= ?"), eq(Long.class), any(Timestamp.class), any(Timestamp.class)))
                .thenReturn(0L);
        when(jdbcOperations.queryForObject("select min(expires_at) from replay_store", Timestamp.class))
                .thenReturn(null);
        when(jdbcOperations.queryForObject("select max(expires_at) from replay_store", Timestamp.class))
                .thenReturn(null);

        PrivacyAuditDeadLetterWebhookReplayStoreSnapshot snapshot = store.snapshotStats(now, Duration.ofMinutes(5));

        assertThat(snapshot.count()).isZero();
        assertThat(snapshot.expiringSoon()).isZero();
        assertThat(snapshot.earliestExpiry()).isNull();
        assertThat(snapshot.latestExpiry()).isNull();
    }

    @Test
    void skipsGlobalCleanupWithinInterval() {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        JdbcPrivacyAuditDeadLetterWebhookReplayStore store = new JdbcPrivacyAuditDeadLetterWebhookReplayStore(
                jdbcOperations,
                "replay_store",
                Duration.ofMinutes(10)
        );
        Instant now = Instant.now();

        when(jdbcOperations.update(eq("delete from replay_store where nonce = ? and expires_at < ?"), eq("nonce-1"), any(Timestamp.class)))
                .thenReturn(0);
        when(jdbcOperations.update(eq("delete from replay_store where nonce = ? and expires_at < ?"), eq("nonce-2"), any(Timestamp.class)))
                .thenReturn(0);
        when(jdbcOperations.update(eq("delete from replay_store where expires_at < ?"), any(Timestamp.class))).thenReturn(1);
        when(jdbcOperations.update(contains("insert into replay_store"), eq("nonce-1"), any(Timestamp.class))).thenReturn(1);
        when(jdbcOperations.update(contains("insert into replay_store"), eq("nonce-2"), any(Timestamp.class))).thenReturn(1);

        store.markIfNew("nonce-1", now, Duration.ofMinutes(5));
        store.markIfNew("nonce-2", now.plusSeconds(1), Duration.ofMinutes(5));

        verify(jdbcOperations, times(1)).update(eq("delete from replay_store where expires_at < ?"), any(Timestamp.class));
    }
}
