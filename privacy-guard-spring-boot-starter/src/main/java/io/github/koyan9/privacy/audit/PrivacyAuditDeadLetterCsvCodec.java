/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
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
        StringBuilder builder = new StringBuilder();
        writeHeader(builder);
        if (entries != null) {
            for (PrivacyAuditDeadLetterEntry entry : entries) {
                writeEntry(builder, entry);
            }
        }
        return builder.toString();
    }

    public List<PrivacyAuditDeadLetterEntry> importEntries(String content) {
        List<PrivacyAuditDeadLetterEntry> entries = new ArrayList<>();
        importEntries(new java.io.StringReader(content == null ? "" : content), entries::add);
        return List.copyOf(entries);
    }

    public void writeHeader(Appendable appendable) {
        append(appendable, HEADER);
        append(appendable, "\n");
    }

    public void writeEntry(Appendable appendable, PrivacyAuditDeadLetterEntry entry) {
        append(appendable, csv(entry.id() == null ? null : String.valueOf(entry.id())));
        append(appendable, ",");
        append(appendable, csv(toText(entry.failedAt())));
        append(appendable, ",");
        append(appendable, csv(String.valueOf(entry.attempts())));
        append(appendable, ",");
        append(appendable, csv(entry.errorType()));
        append(appendable, ",");
        append(appendable, csv(entry.errorMessage()));
        append(appendable, ",");
        append(appendable, csv(toText(entry.occurredAt())));
        append(appendable, ",");
        append(appendable, csv(entry.action()));
        append(appendable, ",");
        append(appendable, csv(entry.resourceType()));
        append(appendable, ",");
        append(appendable, csv(entry.resourceId()));
        append(appendable, ",");
        append(appendable, csv(entry.actor()));
        append(appendable, ",");
        append(appendable, csv(entry.outcome()));
        append(appendable, ",");
        append(appendable, csv(toJson(entry.details())));
        append(appendable, "\n");
    }

    public void importEntries(Reader reader, java.util.function.Consumer<PrivacyAuditDeadLetterEntry> consumer) {
        Objects.requireNonNull(reader, "reader must not be null");
        Objects.requireNonNull(consumer, "consumer must not be null");
        try {
            CsvRowReader rowReader = new CsvRowReader(reader);
            boolean headerChecked = false;
            List<String> row;
            while ((row = rowReader.nextRow()) != null) {
                if (!headerChecked) {
                    headerChecked = true;
                    if (isHeader(row)) {
                        continue;
                    }
                }
                if (row.isEmpty() || row.stream().allMatch(String::isBlank)) {
                    continue;
                }
                consumer.accept(fromCsv(row));
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to import privacy audit dead letters from CSV", exception);
        }
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

    private void append(Appendable appendable, String value) {
        try {
            appendable.append(value);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static final class CsvRowReader {

        private final java.io.PushbackReader reader;
        private boolean reachedEof;

        private CsvRowReader(Reader reader) {
            this.reader = new java.io.PushbackReader(reader, 1);
        }

        List<String> nextRow() throws IOException {
            if (reachedEof) {
                return null;
            }
            List<String> currentRow = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            boolean inQuotes = false;
            boolean sawAny = false;

            while (true) {
                int raw = reader.read();
                if (raw == -1) {
                    reachedEof = true;
                    break;
                }
                sawAny = true;
                char ch = (char) raw;
                if (inQuotes) {
                    if (ch == '"') {
                        int next = reader.read();
                        if (next == '"') {
                            current.append('"');
                        } else {
                            inQuotes = false;
                            if (next != -1) {
                                reader.unread(next);
                            }
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
                    return currentRow;
                } else if (ch != '\r') {
                    current.append(ch);
                }
            }

            if (!sawAny && currentRow.isEmpty() && current.length() == 0) {
                return null;
            }
            if (current.length() > 0 || !currentRow.isEmpty()) {
                currentRow.add(current.toString());
            }
            return currentRow.isEmpty() ? null : currentRow;
        }
    }
}
