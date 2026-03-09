/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PrivacyAuditDeadLetterCsvCodec {

    private static final String HEADER = "id,failed_at,attempts,error_type,error_message,occurred_at,action,resource_type,resource_id,actor,outcome,details_json";
    private static final TypeReference<Map<String, String>> DETAILS_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public PrivacyAuditDeadLetterCsvCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public String exportEntries(List<PrivacyAuditDeadLetterEntry> entries) {
        StringBuilder builder = new StringBuilder(HEADER).append('\n');
        for (PrivacyAuditDeadLetterEntry entry : entries) {
            builder.append(csv(entry.id() == null ? null : String.valueOf(entry.id()))).append(',')
                    .append(csv(toText(entry.failedAt()))).append(',')
                    .append(csv(String.valueOf(entry.attempts()))).append(',')
                    .append(csv(entry.errorType())).append(',')
                    .append(csv(entry.errorMessage())).append(',')
                    .append(csv(toText(entry.occurredAt()))).append(',')
                    .append(csv(entry.action())).append(',')
                    .append(csv(entry.resourceType())).append(',')
                    .append(csv(entry.resourceId())).append(',')
                    .append(csv(entry.actor())).append(',')
                    .append(csv(entry.outcome())).append(',')
                    .append(csv(toJson(entry.details())))
                    .append('\n');
        }
        return builder.toString();
    }

    public List<PrivacyAuditDeadLetterEntry> importEntries(String content) {
        List<List<String>> rows = parseCsv(content == null ? "" : content);
        if (rows.isEmpty()) {
            return List.of();
        }
        int startIndex = isHeader(rows.get(0)) ? 1 : 0;
        List<PrivacyAuditDeadLetterEntry> entries = new ArrayList<>();
        for (int index = startIndex; index < rows.size(); index++) {
            List<String> columns = rows.get(index);
            if (columns.isEmpty() || columns.stream().allMatch(String::isBlank)) {
                continue;
            }
            entries.add(fromCsv(columns));
        }
        return List.copyOf(entries);
    }

    private boolean isHeader(List<String> row) {
        return !row.isEmpty() && "id".equalsIgnoreCase(row.get(0));
    }

    private PrivacyAuditDeadLetterEntry fromCsv(List<String> columns) {
        if (columns.size() != 11 && columns.size() != 12) {
            throw new IllegalStateException("Expected 11 or 12 CSV columns for privacy audit dead letter import but found " + columns.size());
        }
        int offset = columns.size() == 12 ? 1 : 0;
        return new PrivacyAuditDeadLetterEntry(
                null,
                parseInstant(columns.get(offset)),
                Integer.parseInt(columns.get(offset + 1)),
                nullIfBlank(columns.get(offset + 2)),
                nullIfBlank(columns.get(offset + 3)),
                parseInstant(columns.get(offset + 4)),
                nullIfBlank(columns.get(offset + 5)),
                nullIfBlank(columns.get(offset + 6)),
                nullIfBlank(columns.get(offset + 7)),
                nullIfBlank(columns.get(offset + 8)),
                nullIfBlank(columns.get(offset + 9)),
                parseDetails(columns.get(offset + 10))
        );
    }

    private String toJson(Map<String, String> details) {
        try {
            return objectMapper.writeValueAsString(details == null ? Map.of() : details);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize privacy audit dead letter details", exception);
        }
    }

    private Map<String, String> parseDetails(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, DETAILS_TYPE);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to parse privacy audit dead letter details JSON", exception);
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalStateException("Failed to parse dead letter instant value: " + value, exception);
        }
    }

    private String toText(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private List<List<String>> parseCsv(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> currentRow = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int index = 0; index < content.length(); index++) {
            char ch = content.charAt(index);
            if (inQuotes) {
                if (ch == '"') {
                    if (index + 1 < content.length() && content.charAt(index + 1) == '"') {
                        current.append('"');
                        index++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                currentRow.add(current.toString());
                current.setLength(0);
            } else if (ch == '\n') {
                currentRow.add(current.toString());
                rows.add(currentRow);
                currentRow = new ArrayList<>();
                current.setLength(0);
            } else if (ch != '\r') {
                current.append(ch);
            }
        }

        if (current.length() > 0 || !currentRow.isEmpty()) {
            currentRow.add(current.toString());
            rows.add(currentRow);
        }
        return rows;
    }
}
