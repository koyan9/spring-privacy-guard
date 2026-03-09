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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcPrivacyAuditRepositoryTest {

    @Test
    void writesAuditEventThroughJdbcOperations() {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        JdbcPrivacyAuditRepository repository = new JdbcPrivacyAuditRepository(jdbcOperations, new ObjectMapper(), "privacy_audit_event");
        PrivacyAuditEvent event = new PrivacyAuditEvent(Instant.now(), "READ", "Patient", "demo", "actor", "OK", Map.of("phone", "138****8000"));

        repository.save(event);

        verify(jdbcOperations).update(
                eq("insert into privacy_audit_event (occurred_at, action, resource_type, resource_id, actor, outcome, details_json) values (?, ?, ?, ?, ?, ?, ?)"),
                any(), any(), any(), any(), any(), any(), eq("{\"phone\":\"138****8000\"}")
        );
    }

    @Test
    void writesAuditEventsInBatchThroughJdbcOperations() {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        JdbcPrivacyAuditRepository repository = new JdbcPrivacyAuditRepository(jdbcOperations, new ObjectMapper(), "privacy_audit_event");
        PrivacyAuditEvent first = new PrivacyAuditEvent(Instant.parse("2026-03-06T00:00:00Z"), "READ", "Patient", "first", "actor", "OK", Map.of("phone", "138****8000"));
        PrivacyAuditEvent second = new PrivacyAuditEvent(Instant.parse("2026-03-06T01:00:00Z"), "WRITE", "Patient", "second", "actor", "OK", Map.of("phone", "139****9000"));

        repository.saveAll(List.of(first, second));

        verify(jdbcOperations).batchUpdate(
                eq("insert into privacy_audit_event (occurred_at, action, resource_type, resource_id, actor, outcome, details_json) values (?, ?, ?, ?, ?, ?, ?)"),
                org.mockito.ArgumentMatchers.<java.util.List<Object[]>>argThat(batchArgs ->
                        batchArgs.size() == 2
                                && Arrays.equals(batchArgs.get(0), new Object[]{Instant.parse("2026-03-06T00:00:00Z"), "READ", "Patient", "first", "actor", "OK", "{\"phone\":\"138****8000\"}"})
                                && Arrays.equals(batchArgs.get(1), new Object[]{Instant.parse("2026-03-06T01:00:00Z"), "WRITE", "Patient", "second", "actor", "OK", "{\"phone\":\"139****9000\"}"})
                )
        );
    }

    @Test
    void readsFilteredSortedAndPagedAuditEventsThroughJdbcOperations() throws Exception {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        JdbcPrivacyAuditRepository repository = new JdbcPrivacyAuditRepository(jdbcOperations, new ObjectMapper(), "privacy_audit_event");

        doAnswer(invocation -> {
            RowMapper<?> rowMapper = invocation.getArgument(1);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.getTimestamp("occurred_at")).thenReturn(Timestamp.from(Instant.parse("2026-03-06T01:00:00Z")));
            when(resultSet.getString("action")).thenReturn("READ");
            when(resultSet.getString("resource_type")).thenReturn("Patient");
            when(resultSet.getString("resource_id")).thenReturn("demo");
            when(resultSet.getString("actor")).thenReturn("actor-123");
            when(resultSet.getString("outcome")).thenReturn("OK");
            when(resultSet.getString("details_json")).thenReturn("{\"phone\":\"138****8000\"}");
            return List.of(rowMapper.mapRow(resultSet, 0));
        }).when(jdbcOperations).query(
                eq("select occurred_at, action, resource_type, resource_id, actor, outcome, details_json from privacy_audit_event where 1=1 and action = ? and resource_type = ? and actor like ? and occurred_at >= ? and occurred_at <= ? order by occurred_at ASC limit ? offset ?"),
                any(RowMapper.class),
                eq("READ"), eq("Patient"), eq("%actor%"), eq(Instant.parse("2026-03-06T00:00:00Z")), eq(Instant.parse("2026-03-06T02:00:00Z")), eq(10), eq(5)
        );

        List<PrivacyAuditEvent> events = repository.findByCriteria(new PrivacyAuditQueryCriteria(
                "READ", null,
                "Patient", null,
                null, null,
                null, "actor",
                null, null,
                Instant.parse("2026-03-06T00:00:00Z"),
                Instant.parse("2026-03-06T02:00:00Z"),
                PrivacyAuditSortDirection.ASC,
                10,
                5
        ));

        assertEquals(1, events.size());
        assertEquals("demo", events.get(0).resourceId());
        assertEquals("138****8000", events.get(0).details().get("phone"));
    }

    @Test
    void computesStatsThroughJdbcOperations() throws Exception {
        JdbcOperations jdbcOperations = mock(JdbcOperations.class);
        JdbcPrivacyAuditRepository repository = new JdbcPrivacyAuditRepository(jdbcOperations, new ObjectMapper(), "privacy_audit_event");

        when(jdbcOperations.queryForObject(eq("select count(*) from privacy_audit_event where 1=1 and actor like ?"), eq(Long.class), eq("%alice%"))).thenReturn(2L);
        doAnswer(invocation -> {
            ResultSetExtractor<?> extractor = invocation.getArgument(1);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.next()).thenReturn(true, false);
            when(resultSet.getString(1)).thenReturn("READ");
            when(resultSet.getLong(2)).thenReturn(2L);
            return extractor.extractData(resultSet);
        }).when(jdbcOperations).query(eq("select action, count(*) as cnt from privacy_audit_event where 1=1 and actor like ? group by action"), any(ResultSetExtractor.class), eq("%alice%"));
        doAnswer(invocation -> {
            ResultSetExtractor<?> extractor = invocation.getArgument(1);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.next()).thenReturn(true, false);
            when(resultSet.getString(1)).thenReturn("OK");
            when(resultSet.getLong(2)).thenReturn(2L);
            return extractor.extractData(resultSet);
        }).when(jdbcOperations).query(eq("select outcome, count(*) as cnt from privacy_audit_event where 1=1 and actor like ? group by outcome"), any(ResultSetExtractor.class), eq("%alice%"));
        doAnswer(invocation -> {
            ResultSetExtractor<?> extractor = invocation.getArgument(1);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.next()).thenReturn(true, false);
            when(resultSet.getString(1)).thenReturn("Patient");
            when(resultSet.getLong(2)).thenReturn(2L);
            return extractor.extractData(resultSet);
        }).when(jdbcOperations).query(eq("select resource_type, count(*) as cnt from privacy_audit_event where 1=1 and actor like ? group by resource_type"), any(ResultSetExtractor.class), eq("%alice%"));

        PrivacyAuditQueryStats stats = repository.computeStats(new PrivacyAuditQueryCriteria(
                null, null,
                null, null,
                null, null,
                null, "alice",
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
    }
}
