/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcPrivacyAuditDeadLetterRepositoryTest {

    @Test
    void writesDeadLetterEntryThroughJdbcOperations() {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        JdbcPrivacyAuditDeadLetterRepository repository = new JdbcPrivacyAuditDeadLetterRepository(jdbcOperations, new ObjectMapper(), "privacy_audit_dead_letter");
        PrivacyAuditDeadLetterEntry entry = new PrivacyAuditDeadLetterEntry(
                null,
                Instant.parse("2026-03-06T00:00:00Z"),
                3,
                "java.lang.IllegalStateException",
                "failure",
                Instant.parse("2026-03-05T23:00:00Z"),
                "READ",
                "Patient",
                "demo",
                "actor",
                "OK",
                Map.of("phone", "138****8000")
        );

        repository.save(entry);

        verify(jdbcOperations).update(
                eq("insert into privacy_audit_dead_letter (failed_at, attempts, error_type, error_message, occurred_at, action, resource_type, resource_id, actor, outcome, details_json) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"),
                any(), eq(3), eq("java.lang.IllegalStateException"), eq("failure"), any(), eq("READ"), eq("Patient"), eq("demo"), eq("actor"), eq("OK"), eq("{\"phone\":\"138****8000\"}")
        );
    }

    @Test
    void writesDeadLetterEntriesInBatchThroughJdbcOperations() {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        JdbcPrivacyAuditDeadLetterRepository repository = new JdbcPrivacyAuditDeadLetterRepository(jdbcOperations, new ObjectMapper(), "privacy_audit_dead_letter");
        PrivacyAuditDeadLetterEntry first = new PrivacyAuditDeadLetterEntry(null, Instant.parse("2026-03-06T00:00:00Z"), 3, "A", "one", Instant.parse("2026-03-05T23:00:00Z"), "READ", "Patient", "first", "actor", "OK", Map.of("phone", "138****8000"));
        PrivacyAuditDeadLetterEntry second = new PrivacyAuditDeadLetterEntry(null, Instant.parse("2026-03-06T01:00:00Z"), 4, "B", "two", Instant.parse("2026-03-06T00:30:00Z"), "WRITE", "Patient", "second", "actor", "OK", Map.of("phone", "139****9000"));

        repository.saveAll(List.of(first, second));

        verify(jdbcOperations).batchUpdate(
                eq("insert into privacy_audit_dead_letter (failed_at, attempts, error_type, error_message, occurred_at, action, resource_type, resource_id, actor, outcome, details_json) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"),
                org.mockito.ArgumentMatchers.<java.util.List<Object[]>>argThat(batchArgs ->
                        batchArgs.size() == 2
                                && Arrays.equals(batchArgs.get(0), new Object[]{Instant.parse("2026-03-06T00:00:00Z"), 3, "A", "one", Instant.parse("2026-03-05T23:00:00Z"), "READ", "Patient", "first", "actor", "OK", "{\"phone\":\"138****8000\"}"})
                                && Arrays.equals(batchArgs.get(1), new Object[]{Instant.parse("2026-03-06T01:00:00Z"), 4, "B", "two", Instant.parse("2026-03-06T00:30:00Z"), "WRITE", "Patient", "second", "actor", "OK", "{\"phone\":\"139****9000\"}"})
                )
        );
    }

    @Test
    void readsFilteredSortedAndPagedDeadLettersThroughJdbcOperations() throws Exception {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        JdbcPrivacyAuditDeadLetterRepository repository = new JdbcPrivacyAuditDeadLetterRepository(jdbcOperations, new ObjectMapper(), "privacy_audit_dead_letter");

        doAnswer(invocation -> {
            RowMapper<?> rowMapper = invocation.getArgument(1);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.getLong("id")).thenReturn(7L);
            when(resultSet.wasNull()).thenReturn(false);
            when(resultSet.getTimestamp("failed_at")).thenReturn(Timestamp.from(Instant.parse("2026-03-06T01:00:00Z")));
            when(resultSet.getInt("attempts")).thenReturn(3);
            when(resultSet.getString("error_type")).thenReturn("java.lang.IllegalStateException");
            when(resultSet.getString("error_message")).thenReturn("failure");
            when(resultSet.getTimestamp("occurred_at")).thenReturn(Timestamp.from(Instant.parse("2026-03-05T23:00:00Z")));
            when(resultSet.getString("action")).thenReturn("READ");
            when(resultSet.getString("resource_type")).thenReturn("Patient");
            when(resultSet.getString("resource_id")).thenReturn("demo");
            when(resultSet.getString("actor")).thenReturn("actor-123");
            when(resultSet.getString("outcome")).thenReturn("OK");
            when(resultSet.getString("details_json")).thenReturn("{\"phone\":\"138****8000\"}");
            return List.of(rowMapper.mapRow(resultSet, 0));
        }).when(jdbcOperations).query(
                eq("select id, failed_at, attempts, error_type, error_message, occurred_at, action, resource_type, resource_id, actor, outcome, details_json from privacy_audit_dead_letter where 1=1 and action = ? and resource_type = ? and actor like ? and error_type = ? and failed_at >= ? and failed_at <= ? order by failed_at ASC, id ASC limit ? offset ?"),
                any(RowMapper.class),
                eq("READ"), eq("Patient"), eq("%actor%"), eq("java.lang.IllegalStateException"), eq(Instant.parse("2026-03-06T00:00:00Z")), eq(Instant.parse("2026-03-06T02:00:00Z")), eq(10), eq(5)
        );

        List<PrivacyAuditDeadLetterEntry> entries = repository.findByCriteria(new PrivacyAuditDeadLetterQueryCriteria(
                "READ", null,
                "Patient", null,
                null, null,
                null, "actor",
                null, null,
                "java.lang.IllegalStateException", null,
                Instant.parse("2026-03-06T00:00:00Z"),
                Instant.parse("2026-03-06T02:00:00Z"),
                null,
                null,
                PrivacyAuditSortDirection.ASC,
                10,
                5
        ));

        assertEquals(1, entries.size());
        assertEquals(7L, entries.get(0).id());
        assertEquals("demo", entries.get(0).resourceId());
    }

    @Test
    void computesStatsThroughJdbcOperations() throws Exception {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        JdbcPrivacyAuditDeadLetterRepository repository = new JdbcPrivacyAuditDeadLetterRepository(jdbcOperations, new ObjectMapper(), "privacy_audit_dead_letter");

        when(jdbcOperations.queryForObject(eq("select count(*) from privacy_audit_dead_letter where 1=1 and actor like ?"), eq(Long.class), eq("%alice%"))).thenReturn(2L);
        doAnswer(invocation -> {
            ResultSetExtractor<?> extractor = invocation.getArgument(1);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.next()).thenReturn(true, false);
            when(resultSet.getString(1)).thenReturn("READ");
            when(resultSet.getLong(2)).thenReturn(2L);
            return extractor.extractData(resultSet);
        }).when(jdbcOperations).query(eq("select action, count(*) as cnt from privacy_audit_dead_letter where 1=1 and actor like ? group by action"), any(ResultSetExtractor.class), eq("%alice%"));
        doAnswer(invocation -> {
            ResultSetExtractor<?> extractor = invocation.getArgument(1);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.next()).thenReturn(true, false);
            when(resultSet.getString(1)).thenReturn("OK");
            when(resultSet.getLong(2)).thenReturn(2L);
            return extractor.extractData(resultSet);
        }).when(jdbcOperations).query(eq("select outcome, count(*) as cnt from privacy_audit_dead_letter where 1=1 and actor like ? group by outcome"), any(ResultSetExtractor.class), eq("%alice%"));
        doAnswer(invocation -> {
            ResultSetExtractor<?> extractor = invocation.getArgument(1);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.next()).thenReturn(true, false);
            when(resultSet.getString(1)).thenReturn("Patient");
            when(resultSet.getLong(2)).thenReturn(2L);
            return extractor.extractData(resultSet);
        }).when(jdbcOperations).query(eq("select resource_type, count(*) as cnt from privacy_audit_dead_letter where 1=1 and actor like ? group by resource_type"), any(ResultSetExtractor.class), eq("%alice%"));
        doAnswer(invocation -> {
            ResultSetExtractor<?> extractor = invocation.getArgument(1);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.next()).thenReturn(true, false);
            when(resultSet.getString(1)).thenReturn("java.lang.IllegalStateException");
            when(resultSet.getLong(2)).thenReturn(2L);
            return extractor.extractData(resultSet);
        }).when(jdbcOperations).query(eq("select error_type, count(*) as cnt from privacy_audit_dead_letter where 1=1 and actor like ? group by error_type"), any(ResultSetExtractor.class), eq("%alice%"));

        PrivacyAuditDeadLetterStats stats = repository.computeStats(new PrivacyAuditDeadLetterQueryCriteria(
                null, null,
                null, null,
                null, null,
                null, "alice",
                null, null,
                null, null,
                null, null,
                null, null,
                PrivacyAuditSortDirection.DESC,
                100,
                0
        ));

        assertEquals(2, stats.total());
        assertEquals(2L, stats.byAction().get("READ"));
        assertEquals(2L, stats.byOutcome().get("OK"));
        assertEquals(2L, stats.byResourceType().get("Patient"));
        assertEquals(2L, stats.byErrorType().get("java.lang.IllegalStateException"));
    }

    @Test
    void readsAndDeletesDeadLetterEntries() throws Exception {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        JdbcPrivacyAuditDeadLetterRepository repository = new JdbcPrivacyAuditDeadLetterRepository(jdbcOperations, new ObjectMapper(), "privacy_audit_dead_letter");

        doAnswer(invocation -> {
            RowMapper<?> rowMapper = invocation.getArgument(1);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.getLong("id")).thenReturn(7L);
            when(resultSet.wasNull()).thenReturn(false);
            when(resultSet.getTimestamp("failed_at")).thenReturn(Timestamp.from(Instant.parse("2026-03-06T00:00:00Z")));
            when(resultSet.getInt("attempts")).thenReturn(3);
            when(resultSet.getString("error_type")).thenReturn("java.lang.IllegalStateException");
            when(resultSet.getString("error_message")).thenReturn("failure");
            when(resultSet.getTimestamp("occurred_at")).thenReturn(Timestamp.from(Instant.parse("2026-03-05T23:00:00Z")));
            when(resultSet.getString("action")).thenReturn("READ");
            when(resultSet.getString("resource_type")).thenReturn("Patient");
            when(resultSet.getString("resource_id")).thenReturn("demo");
            when(resultSet.getString("actor")).thenReturn("actor");
            when(resultSet.getString("outcome")).thenReturn("OK");
            when(resultSet.getString("details_json")).thenReturn("{\"phone\":\"138****8000\"}");
            return List.of(rowMapper.mapRow(resultSet, 0));
        }).when(jdbcOperations).query(eq("select id, failed_at, attempts, error_type, error_message, occurred_at, action, resource_type, resource_id, actor, outcome, details_json from privacy_audit_dead_letter where id = ?"), any(RowMapper.class), eq(7L));
        when(jdbcOperations.update(eq("delete from privacy_audit_dead_letter where id = ?"), eq(7L))).thenReturn(1);

        Optional<PrivacyAuditDeadLetterEntry> entry = repository.findById(7L);
        boolean deleted = repository.deleteById(7L);

        assertTrue(entry.isPresent());
        assertEquals(7L, entry.get().id());
        assertEquals("demo", entry.get().resourceId());
        assertEquals("138****8000", entry.get().details().get("phone"));
        assertTrue(deleted);
    }

    @Test
    void readsTenantNativeFilteredDeadLettersThroughJdbcOperations() throws Exception {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        JdbcPrivacyAuditDeadLetterRepository repository = new JdbcPrivacyAuditDeadLetterRepository(jdbcOperations, new ObjectMapper(), "privacy_audit_dead_letter");

        doAnswer(invocation -> {
            RowMapper<?> rowMapper = invocation.getArgument(1);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.getLong("id")).thenReturn(7L);
            when(resultSet.wasNull()).thenReturn(false);
            when(resultSet.getTimestamp("failed_at")).thenReturn(Timestamp.from(Instant.parse("2026-03-06T01:00:00Z")));
            when(resultSet.getInt("attempts")).thenReturn(3);
            when(resultSet.getString("error_type")).thenReturn("java.lang.IllegalStateException");
            when(resultSet.getString("error_message")).thenReturn("failure");
            when(resultSet.getTimestamp("occurred_at")).thenReturn(Timestamp.from(Instant.parse("2026-03-05T23:00:00Z")));
            when(resultSet.getString("action")).thenReturn("READ");
            when(resultSet.getString("resource_type")).thenReturn("Patient");
            when(resultSet.getString("resource_id")).thenReturn("tenant-demo");
            when(resultSet.getString("actor")).thenReturn("actor");
            when(resultSet.getString("outcome")).thenReturn("OK");
            when(resultSet.getString("details_json")).thenReturn("{\"tenant\":\"tenant-a\"}");
            return List.of(rowMapper.mapRow(resultSet, 0));
        }).when(jdbcOperations).query(
                eq("select id, failed_at, attempts, error_type, error_message, occurred_at, action, resource_type, resource_id, actor, outcome, details_json from privacy_audit_dead_letter where 1=1 and action = ? and details_json like ? escape '\\' order by failed_at DESC, id DESC limit ? offset ?"),
                any(RowMapper.class),
                eq("READ"), eq("%\"tenant\":\"tenant-a\"%"), eq(10), eq(0)
        );

        List<PrivacyAuditDeadLetterEntry> entries = repository.findByCriteria(
                "tenant-a",
                "tenant",
                new PrivacyAuditDeadLetterQueryCriteria(
                        "READ", null,
                        null, null,
                        null, null,
                        null, null,
                        null, null,
                        null, null,
                        null, null,
                        null, null,
                        PrivacyAuditSortDirection.DESC,
                        10,
                        0
                )
        );

        assertEquals(1, entries.size());
        assertEquals("tenant-demo", entries.get(0).resourceId());
    }

    @Test
    void computesTenantNativeStatsThroughJdbcOperations() throws Exception {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        JdbcPrivacyAuditDeadLetterRepository repository = new JdbcPrivacyAuditDeadLetterRepository(jdbcOperations, new ObjectMapper(), "privacy_audit_dead_letter");

        when(jdbcOperations.queryForObject(
                eq("select count(*) from privacy_audit_dead_letter where 1=1 and details_json like ? escape '\\'"),
                eq(Long.class),
                eq("%\"tenant\":\"tenant-a\"%")
        )).thenReturn(2L);
        doAnswer(invocation -> {
            ResultSetExtractor<?> extractor = invocation.getArgument(1);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.next()).thenReturn(true, false);
            when(resultSet.getString(1)).thenReturn("READ");
            when(resultSet.getLong(2)).thenReturn(2L);
            return extractor.extractData(resultSet);
        }).when(jdbcOperations).query(
                eq("select action, count(*) as cnt from privacy_audit_dead_letter where 1=1 and details_json like ? escape '\\' group by action"),
                any(ResultSetExtractor.class),
                eq("%\"tenant\":\"tenant-a\"%")
        );
        doAnswer(invocation -> {
            ResultSetExtractor<?> extractor = invocation.getArgument(1);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.next()).thenReturn(true, false);
            when(resultSet.getString(1)).thenReturn("OK");
            when(resultSet.getLong(2)).thenReturn(2L);
            return extractor.extractData(resultSet);
        }).when(jdbcOperations).query(
                eq("select outcome, count(*) as cnt from privacy_audit_dead_letter where 1=1 and details_json like ? escape '\\' group by outcome"),
                any(ResultSetExtractor.class),
                eq("%\"tenant\":\"tenant-a\"%")
        );
        doAnswer(invocation -> {
            ResultSetExtractor<?> extractor = invocation.getArgument(1);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.next()).thenReturn(true, false);
            when(resultSet.getString(1)).thenReturn("Patient");
            when(resultSet.getLong(2)).thenReturn(2L);
            return extractor.extractData(resultSet);
        }).when(jdbcOperations).query(
                eq("select resource_type, count(*) as cnt from privacy_audit_dead_letter where 1=1 and details_json like ? escape '\\' group by resource_type"),
                any(ResultSetExtractor.class),
                eq("%\"tenant\":\"tenant-a\"%")
        );
        doAnswer(invocation -> {
            ResultSetExtractor<?> extractor = invocation.getArgument(1);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.next()).thenReturn(true, false);
            when(resultSet.getString(1)).thenReturn("java.lang.IllegalStateException");
            when(resultSet.getLong(2)).thenReturn(2L);
            return extractor.extractData(resultSet);
        }).when(jdbcOperations).query(
                eq("select error_type, count(*) as cnt from privacy_audit_dead_letter where 1=1 and details_json like ? escape '\\' group by error_type"),
                any(ResultSetExtractor.class),
                eq("%\"tenant\":\"tenant-a\"%")
        );

        PrivacyAuditDeadLetterStats stats = repository.computeStats("tenant-a", "tenant", PrivacyAuditDeadLetterQueryCriteria.recent(10));

        assertEquals(2, stats.total());
        assertEquals(2L, stats.byAction().get("READ"));
        assertEquals(2L, stats.byOutcome().get("OK"));
        assertEquals(2L, stats.byResourceType().get("Patient"));
        assertEquals(2L, stats.byErrorType().get("java.lang.IllegalStateException"));
    }

    @Test
    void writesDeadLetterEntryWithConfiguredTenantColumn() {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        JdbcPrivacyAuditDeadLetterRepository repository = new JdbcPrivacyAuditDeadLetterRepository(
                jdbcOperations,
                new ObjectMapper(),
                "privacy_audit_dead_letter",
                "tenant_id",
                "tenant"
        );
        PrivacyAuditDeadLetterEntry entry = new PrivacyAuditDeadLetterEntry(
                null,
                Instant.parse("2026-03-06T00:00:00Z"),
                3,
                "java.lang.IllegalStateException",
                "failure",
                Instant.parse("2026-03-05T23:00:00Z"),
                "READ",
                "Patient",
                "demo",
                "actor",
                "OK",
                Map.of("tenant", "tenant-a", "phone", "138****8000")
        );

        repository.save(entry);

        verify(jdbcOperations).update(
                eq("insert into privacy_audit_dead_letter (failed_at, attempts, error_type, error_message, occurred_at, action, resource_type, resource_id, actor, outcome, details_json, tenant_id) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"),
                any(), eq(3), eq("java.lang.IllegalStateException"), eq("failure"), any(), eq("READ"), eq("Patient"), eq("demo"), eq("actor"), eq("OK"),
                argThat((String json) -> json.contains("\"tenant\":\"tenant-a\"") && json.contains("\"phone\":\"138****8000\"")),
                eq("tenant-a")
        );
    }

    @Test
    void readsTenantNativeFilteredDeadLettersThroughConfiguredTenantColumn() throws Exception {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        JdbcPrivacyAuditDeadLetterRepository repository = new JdbcPrivacyAuditDeadLetterRepository(
                jdbcOperations,
                new ObjectMapper(),
                "privacy_audit_dead_letter",
                "tenant_id",
                "tenant"
        );

        doAnswer(invocation -> {
            RowMapper<?> rowMapper = invocation.getArgument(1);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.getLong("id")).thenReturn(7L);
            when(resultSet.wasNull()).thenReturn(false);
            when(resultSet.getTimestamp("failed_at")).thenReturn(Timestamp.from(Instant.parse("2026-03-06T01:00:00Z")));
            when(resultSet.getInt("attempts")).thenReturn(3);
            when(resultSet.getString("error_type")).thenReturn("java.lang.IllegalStateException");
            when(resultSet.getString("error_message")).thenReturn("failure");
            when(resultSet.getTimestamp("occurred_at")).thenReturn(Timestamp.from(Instant.parse("2026-03-05T23:00:00Z")));
            when(resultSet.getString("action")).thenReturn("READ");
            when(resultSet.getString("resource_type")).thenReturn("Patient");
            when(resultSet.getString("resource_id")).thenReturn("tenant-column-demo");
            when(resultSet.getString("actor")).thenReturn("actor");
            when(resultSet.getString("outcome")).thenReturn("OK");
            when(resultSet.getString("details_json")).thenReturn("{\"tenant\":\"tenant-a\"}");
            return List.of(rowMapper.mapRow(resultSet, 0));
        }).when(jdbcOperations).query(
                eq("select id, failed_at, attempts, error_type, error_message, occurred_at, action, resource_type, resource_id, actor, outcome, details_json from privacy_audit_dead_letter where 1=1 and action = ? and tenant_id = ? order by failed_at DESC, id DESC limit ? offset ?"),
                any(RowMapper.class),
                eq("READ"), eq("tenant-a"), eq(10), eq(0)
        );

        List<PrivacyAuditDeadLetterEntry> entries = repository.findByCriteria(
                "tenant-a",
                "tenant",
                new PrivacyAuditDeadLetterQueryCriteria("READ", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, PrivacyAuditSortDirection.DESC, 10, 0)
        );

        assertEquals(1, entries.size());
        assertEquals("tenant-column-demo", entries.get(0).resourceId());
    }

    @Test
    void deletesTenantNativeFilteredDeadLettersThroughJdbcOperations() {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        JdbcPrivacyAuditDeadLetterRepository repository = new JdbcPrivacyAuditDeadLetterRepository(
                jdbcOperations,
                new ObjectMapper(),
                "privacy_audit_dead_letter"
        );
        when(jdbcOperations.update(
                eq("delete from privacy_audit_dead_letter where id in (select id from (select id from privacy_audit_dead_letter where 1=1 and action = ? and details_json like ? escape '\\' order by failed_at DESC, id DESC limit ? offset ?) tenant_scope)"),
                eq("READ"), eq("%\"tenant\":\"tenant-a\"%"), eq(10), eq(5)
        )).thenReturn(2);

        int deleted = repository.deleteByCriteria(
                "tenant-a",
                "tenant",
                new PrivacyAuditDeadLetterQueryCriteria(
                        "READ", null,
                        null, null,
                        null, null,
                        null, null,
                        null, null,
                        null, null,
                        null, null,
                        null, null,
                        PrivacyAuditSortDirection.DESC,
                        10,
                        5
                )
        );

        assertEquals(2, deleted);
    }

    @Test
    void deletesTenantNativeFilteredDeadLettersThroughConfiguredTenantColumn() {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        JdbcPrivacyAuditDeadLetterRepository repository = new JdbcPrivacyAuditDeadLetterRepository(
                jdbcOperations,
                new ObjectMapper(),
                "privacy_audit_dead_letter",
                "tenant_id",
                "tenant"
        );
        when(jdbcOperations.update(
                eq("delete from privacy_audit_dead_letter where id in (select id from (select id from privacy_audit_dead_letter where 1=1 and action = ? and tenant_id = ? order by failed_at DESC, id DESC limit ? offset ?) tenant_scope)"),
                eq("READ"), eq("tenant-a"), eq(10), eq(0)
        )).thenReturn(1);

        int deleted = repository.deleteByCriteria(
                "tenant-a",
                "tenant",
                new PrivacyAuditDeadLetterQueryCriteria(
                        "READ", null,
                        null, null,
                        null, null,
                        null, null,
                        null, null,
                        null, null,
                        null, null,
                        null, null,
                        PrivacyAuditSortDirection.DESC,
                        10,
                        0
                )
        );

        assertEquals(1, deleted);
    }

    @Test
    void replaysTenantNativeFilteredDeadLettersThroughJdbcOperations() throws Exception {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        JdbcPrivacyAuditDeadLetterRepository repository = new JdbcPrivacyAuditDeadLetterRepository(
                jdbcOperations,
                new ObjectMapper(),
                "privacy_audit_dead_letter",
                "tenant_id",
                "tenant"
        );

        doAnswer(invocation -> {
            RowMapper<?> rowMapper = invocation.getArgument(1);
            ResultSet first = mock(ResultSet.class);
            when(first.getLong("id")).thenReturn(7L);
            when(first.wasNull()).thenReturn(false);
            when(first.getTimestamp("failed_at")).thenReturn(Timestamp.from(Instant.parse("2026-03-06T01:00:00Z")));
            when(first.getInt("attempts")).thenReturn(3);
            when(first.getString("error_type")).thenReturn("java.lang.IllegalStateException");
            when(first.getString("error_message")).thenReturn("failure");
            when(first.getTimestamp("occurred_at")).thenReturn(Timestamp.from(Instant.parse("2026-03-05T23:00:00Z")));
            when(first.getString("action")).thenReturn("READ");
            when(first.getString("resource_type")).thenReturn("Patient");
            when(first.getString("resource_id")).thenReturn("tenant-column-a1");
            when(first.getString("actor")).thenReturn("actor");
            when(first.getString("outcome")).thenReturn("OK");
            when(first.getString("details_json")).thenReturn("{\"tenant\":\"tenant-a\"}");

            ResultSet second = mock(ResultSet.class);
            when(second.getLong("id")).thenReturn(8L);
            when(second.wasNull()).thenReturn(false);
            when(second.getTimestamp("failed_at")).thenReturn(Timestamp.from(Instant.parse("2026-03-06T00:00:00Z")));
            when(second.getInt("attempts")).thenReturn(3);
            when(second.getString("error_type")).thenReturn("java.lang.IllegalStateException");
            when(second.getString("error_message")).thenReturn("failure");
            when(second.getTimestamp("occurred_at")).thenReturn(Timestamp.from(Instant.parse("2026-03-05T22:00:00Z")));
            when(second.getString("action")).thenReturn("READ");
            when(second.getString("resource_type")).thenReturn("Patient");
            when(second.getString("resource_id")).thenReturn("tenant-column-a2");
            when(second.getString("actor")).thenReturn("actor");
            when(second.getString("outcome")).thenReturn("OK");
            when(second.getString("details_json")).thenReturn("{\"tenant\":\"tenant-a\"}");
            return List.of(rowMapper.mapRow(first, 0), rowMapper.mapRow(second, 1));
        }).when(jdbcOperations).query(
                eq("select id, failed_at, attempts, error_type, error_message, occurred_at, action, resource_type, resource_id, actor, outcome, details_json from privacy_audit_dead_letter where 1=1 and tenant_id = ? order by failed_at DESC, id DESC limit ? offset ?"),
                any(RowMapper.class),
                eq("tenant-a"), eq(10), eq(0)
        );
        when(jdbcOperations.update(eq("delete from privacy_audit_dead_letter where id = ?"), eq(7L))).thenReturn(1);
        when(jdbcOperations.update(eq("delete from privacy_audit_dead_letter where id = ?"), eq(8L))).thenReturn(1);

        PrivacyAuditDeadLetterReplayResult result = repository.replayByCriteria(
                "tenant-a",
                "tenant",
                PrivacyAuditDeadLetterQueryCriteria.recent(10),
                entry -> !"tenant-column-a2".equals(entry.resourceId())
        );

        assertEquals(2, result.requested());
        assertEquals(1, result.replayed());
        assertEquals(1, result.failed());
        assertEquals(List.of(7L), result.replayedIds());
        assertEquals(List.of(8L), result.failedIds());
    }
}
