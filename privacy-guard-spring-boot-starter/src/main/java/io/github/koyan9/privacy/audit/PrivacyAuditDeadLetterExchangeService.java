/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.audit;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

public class PrivacyAuditDeadLetterExchangeService {

    private static final int DEFAULT_EXPORT_PAGE_SIZE = 500;
    private static final int DEFAULT_IMPORT_BATCH_SIZE = 500;
    private static final int DEFAULT_DEDUP_PAGE_SIZE = 500;

    private final PrivacyAuditDeadLetterRepository deadLetterRepository;
    private final ObjectMapper objectMapper;
    private final PrivacyAuditDeadLetterCsvCodec csvCodec;

    public PrivacyAuditDeadLetterExchangeService(
            PrivacyAuditDeadLetterRepository deadLetterRepository,
            ObjectMapper objectMapper,
            PrivacyAuditDeadLetterCsvCodec csvCodec
    ) {
        this.deadLetterRepository = Objects.requireNonNull(deadLetterRepository, "deadLetterRepository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.csvCodec = Objects.requireNonNull(csvCodec, "csvCodec must not be null");
    }

    public String exportJson(PrivacyAuditDeadLetterQueryCriteria criteria) {
        return exportJson(criteria, DEFAULT_EXPORT_PAGE_SIZE);
    }

    public String exportCsv(PrivacyAuditDeadLetterQueryCriteria criteria) {
        return exportCsv(criteria, DEFAULT_EXPORT_PAGE_SIZE);
    }

    public PrivacyAuditDeadLetterExportManifest exportManifest(PrivacyAuditDeadLetterQueryCriteria criteria, String format) {
        return exportManifest(criteria, format, DEFAULT_EXPORT_PAGE_SIZE);
    }

    public PrivacyAuditDeadLetterExportManifest exportManifest(
            PrivacyAuditDeadLetterQueryCriteria criteria,
            String format,
            int pageSize
    ) {
        String normalizedFormat = normalizeFormat(format);
        MessageDigest digest = sha256Digest();
        DigestingWriter writer = new DigestingWriter(digest);
        int total = switch (normalizedFormat) {
            case "json" -> exportJson(criteria, writer, pageSize);
            case "csv" -> exportCsv(criteria, writer, pageSize);
            default -> throw new IllegalArgumentException("Unsupported dead-letter export format: " + format);
        };
        return new PrivacyAuditDeadLetterExportManifest(normalizedFormat, total, Instant.now(), toHex(digest.digest()));
    }

    public PrivacyAuditDeadLetterImportResult importJson(String content, boolean deduplicate, String expectedChecksum) {
        String resolvedContent = content == null ? "" : content;
        String checksum = sha256(resolvedContent);
        verifyChecksum(checksum, expectedChecksum);
        try (JsonParser parser = objectMapper.getFactory().createParser(new StringReader(resolvedContent))) {
            return importJson(parser, deduplicate, checksum, DEFAULT_IMPORT_BATCH_SIZE);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to import privacy audit dead letters from JSON", exception);
        }
    }

    public PrivacyAuditDeadLetterImportResult importCsv(String content, boolean deduplicate, String expectedChecksum) {
        String resolvedContent = content == null ? "" : content;
        String checksum = sha256(resolvedContent);
        verifyChecksum(checksum, expectedChecksum);
        return importCsv(new StringReader(resolvedContent), deduplicate, checksum, DEFAULT_IMPORT_BATCH_SIZE);
    }

    public String exportJson(PrivacyAuditDeadLetterQueryCriteria criteria, int pageSize) {
        StringWriter writer = new StringWriter();
        exportJson(criteria, writer, pageSize);
        return writer.toString();
    }

    public String exportCsv(PrivacyAuditDeadLetterQueryCriteria criteria, int pageSize) {
        StringWriter writer = new StringWriter();
        exportCsv(criteria, writer, pageSize);
        return writer.toString();
    }

    public int exportJson(PrivacyAuditDeadLetterQueryCriteria criteria, Writer writer, int pageSize) {
        Objects.requireNonNull(writer, "writer must not be null");
        try (JsonGenerator generator = objectMapper.getFactory().createGenerator(writer)) {
            generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
            generator.setCodec(objectMapper);
            generator.writeStartArray();
            int total = exportPaged(criteria, pageSize, entry -> {
                generator.writeObject(entry);
            });
            generator.writeEndArray();
            generator.flush();
            return total;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to export privacy audit dead letters as JSON", exception);
        }
    }

    public int exportCsv(PrivacyAuditDeadLetterQueryCriteria criteria, Writer writer, int pageSize) {
        Objects.requireNonNull(writer, "writer must not be null");
        try {
            csvCodec.writeHeader(writer);
            int total = exportPaged(criteria, pageSize, entry -> csvCodec.writeEntry(writer, entry));
            writer.flush();
            return total;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to export privacy audit dead letters as CSV", exception);
        }
    }

    public PrivacyAuditDeadLetterImportResult importJson(InputStream inputStream, boolean deduplicate, String expectedChecksum) {
        return importJson(inputStream, deduplicate, expectedChecksum, DEFAULT_IMPORT_BATCH_SIZE);
    }

    public PrivacyAuditDeadLetterImportResult importCsv(InputStream inputStream, boolean deduplicate, String expectedChecksum) {
        return importCsv(inputStream, deduplicate, expectedChecksum, DEFAULT_IMPORT_BATCH_SIZE);
    }

    public PrivacyAuditDeadLetterImportResult importJson(
            InputStream inputStream,
            boolean deduplicate,
            String expectedChecksum,
            int batchSize
    ) {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        if (expectedChecksum == null || expectedChecksum.isBlank()) {
            MessageDigest digest = sha256Digest();
            try (java.security.DigestInputStream stream = new java.security.DigestInputStream(inputStream, digest);
                 JsonParser parser = objectMapper.getFactory().createParser(stream)) {
                PrivacyAuditDeadLetterImportResult result = importJson(parser, deduplicate, "", normalizeBatchSize(batchSize));
                return new PrivacyAuditDeadLetterImportResult(
                        result.received(),
                        result.imported(),
                        result.skippedDuplicates(),
                        toHex(digest.digest())
                );
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to import privacy audit dead letters from JSON", exception);
            }
        }
        StagedImport staged = stageImport(inputStream, expectedChecksum);
        try (InputStream stream = Files.newInputStream(staged.path());
             JsonParser parser = objectMapper.getFactory().createParser(stream)) {
            return importJson(parser, deduplicate, staged.checksum(), normalizeBatchSize(batchSize));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to import privacy audit dead letters from JSON", exception);
        } finally {
            cleanupTempFile(staged.path());
        }
    }

    public PrivacyAuditDeadLetterImportResult importCsv(
            InputStream inputStream,
            boolean deduplicate,
            String expectedChecksum,
            int batchSize
    ) {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        if (expectedChecksum == null || expectedChecksum.isBlank()) {
            MessageDigest digest = sha256Digest();
            try (java.security.DigestInputStream stream = new java.security.DigestInputStream(inputStream, digest)) {
                PrivacyAuditDeadLetterImportResult result = importCsv(new java.io.InputStreamReader(stream, StandardCharsets.UTF_8), deduplicate, "", normalizeBatchSize(batchSize));
                return new PrivacyAuditDeadLetterImportResult(
                        result.received(),
                        result.imported(),
                        result.skippedDuplicates(),
                        toHex(digest.digest())
                );
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to import privacy audit dead letters from CSV", exception);
            }
        }
        StagedImport staged = stageImport(inputStream, expectedChecksum);
        try (InputStream stream = Files.newInputStream(staged.path())) {
            return importCsv(new java.io.InputStreamReader(stream, StandardCharsets.UTF_8), deduplicate, staged.checksum(), normalizeBatchSize(batchSize));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to import privacy audit dead letters from CSV", exception);
        } finally {
            cleanupTempFile(staged.path());
        }
    }

    PrivacyAuditDeadLetterImportResult importEntries(
            List<PrivacyAuditDeadLetterEntry> entries,
            boolean deduplicate,
            String checksum
    ) {
        return importEntries(entries, deduplicate, checksum, deadLetterRepository::saveAll);
    }

    PrivacyAuditDeadLetterImportResult importEntries(
            List<PrivacyAuditDeadLetterEntry> entries,
            boolean deduplicate,
            String checksum,
            BatchSaver batchSaver
    ) {
        ImportAccumulator accumulator = new ImportAccumulator(deduplicate, DEFAULT_IMPORT_BATCH_SIZE, batchSaver);
        if (entries != null) {
            for (PrivacyAuditDeadLetterEntry entry : entries) {
                accumulator.accept(entry);
            }
        }
        return accumulator.finish(checksum);
    }

    private PrivacyAuditDeadLetterImportResult importJson(
            JsonParser parser,
            boolean deduplicate,
            String checksum,
            int batchSize
    ) throws IOException {
        parser.setCodec(objectMapper);
        ImportAccumulator accumulator = new ImportAccumulator(deduplicate, batchSize, deadLetterRepository::saveAll);
        JsonToken token = parser.nextToken();
        if (token == null) {
            return accumulator.finish(checksum);
        }
        if (token == JsonToken.START_ARRAY) {
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                PrivacyAuditDeadLetterEntry entry = parser.readValueAs(PrivacyAuditDeadLetterEntry.class);
                accumulator.accept(entry);
            }
        } else {
            PrivacyAuditDeadLetterEntry entry = parser.readValueAs(PrivacyAuditDeadLetterEntry.class);
            accumulator.accept(entry);
        }
        return accumulator.finish(checksum);
    }

    private PrivacyAuditDeadLetterImportResult importCsv(
            java.io.Reader reader,
            boolean deduplicate,
            String checksum,
            int batchSize
    ) {
        ImportAccumulator accumulator = new ImportAccumulator(deduplicate, batchSize, deadLetterRepository::saveAll);
        csvCodec.importEntries(reader, accumulator::accept);
        return accumulator.finish(checksum);
    }

    private int exportPaged(
            PrivacyAuditDeadLetterQueryCriteria criteria,
            int pageSize,
            EntryWriter writer
    ) throws IOException {
        PrivacyAuditDeadLetterQueryCriteria normalized = normalize(criteria);
        int remaining = normalized.limit();
        int offset = normalized.offset();
        int resolvedPageSize = normalizePageSize(pageSize, remaining);
        int total = 0;

        while (remaining > 0) {
            int limit = Math.min(resolvedPageSize, remaining);
            PrivacyAuditDeadLetterQueryCriteria pageCriteria = overrideLimitOffset(normalized, limit, offset);
            List<PrivacyAuditDeadLetterEntry> page = deadLetterRepository.findByCriteria(pageCriteria);
            if (page.isEmpty()) {
                break;
            }
            for (PrivacyAuditDeadLetterEntry entry : page) {
                writer.write(entry);
                total++;
                remaining--;
            }
            offset += page.size();
            if (page.size() < limit) {
                break;
            }
        }
        return total;
    }

    private PrivacyAuditDeadLetterQueryCriteria normalize(PrivacyAuditDeadLetterQueryCriteria criteria) {
        return criteria == null ? PrivacyAuditDeadLetterQueryCriteria.recent(100) : criteria.normalize();
    }

    private PrivacyAuditDeadLetterEntry normalizeImportedEntry(PrivacyAuditDeadLetterEntry entry) {
        return new PrivacyAuditDeadLetterEntry(
                null,
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
                entry.details()
        );
    }

    private void verifyChecksum(String actual, String expectedChecksum) {
        if (expectedChecksum == null || expectedChecksum.isBlank()) {
            return;
        }
        if (!actual.equalsIgnoreCase(expectedChecksum.trim())) {
            throw new IllegalArgumentException("Dead-letter import checksum mismatch");
        }
    }

    private String normalizeFormat(String format) {
        if (format == null || format.isBlank()) {
            return "json";
        }
        return format.trim().toLowerCase();
    }

    private String fingerprint(PrivacyAuditDeadLetterEntry entry) {
        Map<String, String> sortedDetails = new TreeMap<>(entry.details());
        String canonical = String.join("|",
                safe(entry.failedAt()),
                String.valueOf(entry.attempts()),
                safe(entry.errorType()),
                safe(entry.errorMessage()),
                safe(entry.occurredAt()),
                safe(entry.action()),
                safe(entry.resourceType()),
                safe(entry.resourceId()),
                safe(entry.actor()),
                safe(entry.outcome()),
                sortedDetails.toString()
        );
        return sha256(canonical);
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String sha256(String content) {
        MessageDigest digest = sha256Digest();
        byte[] hash = digest.digest((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
        return toHex(hash);
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
    }

    private String toHex(byte[] hash) {
        return HexFormat.of().formatHex(hash);
    }

    private int normalizePageSize(int pageSize, int remaining) {
        if (pageSize <= 0) {
            return Math.min(Math.max(1, remaining), DEFAULT_EXPORT_PAGE_SIZE);
        }
        return Math.max(1, pageSize);
    }

    private int normalizeBatchSize(int batchSize) {
        return Math.max(1, batchSize);
    }

    private PrivacyAuditDeadLetterQueryCriteria overrideLimitOffset(
            PrivacyAuditDeadLetterQueryCriteria criteria,
            int limit,
            int offset
    ) {
        return new PrivacyAuditDeadLetterQueryCriteria(
                criteria.action(),
                criteria.actionLike(),
                criteria.resourceType(),
                criteria.resourceTypeLike(),
                criteria.resourceId(),
                criteria.resourceIdLike(),
                criteria.actor(),
                criteria.actorLike(),
                criteria.outcome(),
                criteria.outcomeLike(),
                criteria.errorType(),
                criteria.errorMessageLike(),
                criteria.failedFrom(),
                criteria.failedTo(),
                criteria.occurredFrom(),
                criteria.occurredTo(),
                criteria.sortDirection(),
                limit,
                offset
        );
    }

    private Map<String, PrivacyAuditDeadLetterEntry> loadExistingFingerprints(boolean deduplicate) {
        if (!deduplicate) {
            return Map.of();
        }
        Map<String, PrivacyAuditDeadLetterEntry> fingerprints = new LinkedHashMap<>();
        PrivacyAuditDeadLetterQueryCriteria base = PrivacyAuditDeadLetterQueryCriteria.recent(DEFAULT_DEDUP_PAGE_SIZE);
        int offset = 0;
        while (true) {
            PrivacyAuditDeadLetterQueryCriteria pageCriteria = overrideLimitOffset(base, DEFAULT_DEDUP_PAGE_SIZE, offset);
            List<PrivacyAuditDeadLetterEntry> page = deadLetterRepository.findByCriteria(pageCriteria);
            if (page.isEmpty()) {
                break;
            }
            for (PrivacyAuditDeadLetterEntry entry : page) {
                fingerprints.put(fingerprint(entry), entry);
            }
            offset += page.size();
            if (page.size() < DEFAULT_DEDUP_PAGE_SIZE) {
                break;
            }
        }
        return fingerprints;
    }

    private StagedImport stageImport(InputStream inputStream, String expectedChecksum) {
        MessageDigest digest = sha256Digest();
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("privacy-dead-letter-import-", ".tmp");
            try (InputStream input = inputStream; java.io.OutputStream output = Files.newOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                }
            }
            String actual = toHex(digest.digest());
            verifyChecksum(actual, expectedChecksum);
            return new StagedImport(tempFile, actual);
        } catch (IOException exception) {
            cleanupTempFile(tempFile);
            throw new IllegalStateException("Failed to stage privacy audit dead letter import", exception);
        }
    }

    private void cleanupTempFile(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private record StagedImport(Path path, String checksum) {
    }

    private interface EntryWriter {
        void write(PrivacyAuditDeadLetterEntry entry) throws IOException;
    }

    private final class ImportAccumulator {

        private final Map<String, PrivacyAuditDeadLetterEntry> fingerprints;
        private final boolean deduplicate;
        private final int batchSize;
        private final BatchSaver batchSaver;
        private final java.util.ArrayList<PrivacyAuditDeadLetterEntry> buffer;
        private final AtomicInteger received = new AtomicInteger();
        private final AtomicInteger skipped = new AtomicInteger();
        private final AtomicInteger imported = new AtomicInteger();

        private ImportAccumulator(boolean deduplicate, int batchSize, BatchSaver batchSaver) {
            this.deduplicate = deduplicate;
            this.batchSize = normalizeBatchSize(batchSize);
            this.batchSaver = batchSaver == null ? deadLetterRepository::saveAll : batchSaver;
            this.fingerprints = new LinkedHashMap<>(loadExistingFingerprints(deduplicate));
            this.buffer = new java.util.ArrayList<>(this.batchSize);
        }

        void accept(PrivacyAuditDeadLetterEntry entry) {
            received.incrementAndGet();
            PrivacyAuditDeadLetterEntry normalized = normalizeImportedEntry(entry);
            if (deduplicate) {
                String fingerprint = fingerprint(normalized);
                if (fingerprints.containsKey(fingerprint)) {
                    skipped.incrementAndGet();
                    return;
                }
                fingerprints.put(fingerprint, normalized);
            }
            buffer.add(normalized);
            if (buffer.size() >= batchSize) {
                flush();
            }
        }

        PrivacyAuditDeadLetterImportResult finish(String checksum) {
            flush();
            return new PrivacyAuditDeadLetterImportResult(
                    received.get(),
                    imported.get(),
                    skipped.get(),
                    checksum
            );
        }

        private void flush() {
            if (buffer.isEmpty()) {
                return;
            }
            batchSaver.save(List.copyOf(buffer));
            imported.addAndGet(buffer.size());
            buffer.clear();
        }
    }

    @FunctionalInterface
    interface BatchSaver {
        void save(List<PrivacyAuditDeadLetterEntry> entries);
    }

    private static final class DigestingWriter extends Writer {

        private final MessageDigest digest;

        private DigestingWriter(MessageDigest digest) {
            this.digest = digest;
        }

        @Override
        public void write(char[] cbuf, int off, int len) {
            if (len <= 0) {
                return;
            }
            byte[] bytes = new String(cbuf, off, len).getBytes(StandardCharsets.UTF_8);
            digest.update(bytes);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

}
