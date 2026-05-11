package com.saptarshi.finogpt.service;

import com.google.genai.Client;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import com.saptarshi.finogpt.config.AppProperties;
import com.saptarshi.finogpt.dto.AppHelpLoadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PreDestroy;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppHelpRagService {

    private static final String SOURCE_DOCUMENT = "app-help-knowledge-base";
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    private final AppProperties appProperties;
    private final LLMService llmService;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ExecutorService loadExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "app-help-loader");
        thread.setDaemon(true);
        return thread;
    });
    private final Object loadMonitor = new Object();
    private volatile LoadStatus loadStatus = LoadStatus.idle();

    public AppHelpLoadResponse startKnowledgeBaseLoad() {
        Path knowledgeBasePath = resolveKnowledgeBasePath();
        validateKnowledgeBasePath(knowledgeBasePath);
        synchronized (loadMonitor) {
            if (loadStatus.state == LoadState.RUNNING) {
                return loadStatus.toResponse();
            }

            loadStatus = LoadStatus.started(knowledgeBasePath.toString(), SOURCE_DOCUMENT);
            loadExecutor.submit(() -> loadKnowledgeBaseInternal(knowledgeBasePath));
            return loadStatus.toResponse();
        }
    }

    public AppHelpLoadResponse getLoadStatus() {
        return loadStatus.toResponse();
    }

    @PreDestroy
    void shutdownLoader() {
        loadExecutor.shutdownNow();
    }

    private void loadKnowledgeBaseInternal(Path knowledgeBasePath) {
        try {
            ensureSchema();

            List<HelpChunk> chunks = parseKnowledgeBase(knowledgeBasePath);
            Map<String, String> existingHashes = loadExistingHashes();
            loadStatus = loadStatus.withTotals(chunks.size());

            int created = 0;
            int updated = 0;
            int unchanged = 0;
            int processed = 0;

            for (HelpChunk chunk : chunks) {
                String existingHash = existingHashes.get(chunk.sourceKey());
                if (chunk.contentHash().equals(existingHash)) {
                    unchanged++;
                    processed++;
                    loadStatus = loadStatus.progress(processed, created, updated, unchanged, 0);
                    continue;
                }

                List<Float> embedding = embed(chunk.embeddingText(), "RETRIEVAL_DOCUMENT", chunk.title());
                upsertChunk(chunk, embedding);

                if (existingHash == null) {
                    created++;
                } else {
                    updated++;
                }

                processed++;
                loadStatus = loadStatus.progress(processed, created, updated, unchanged, 0);
            }

            int deleted = deleteStaleChunks(chunks.stream().map(HelpChunk::sourceKey).toList());
            loadStatus = loadStatus.completed(created, updated, unchanged, deleted);
        } catch (Exception e) {
            log.error("App-help knowledge base load failed", e);
            loadStatus = loadStatus.failed(e.getMessage());
        }
    }

    public String answer(String query) {
        List<RetrievedChunk> matches = retrieve(query);
        if (matches.isEmpty()) {
            return null;
        }

        double bestSimilarity = matches.get(0).similarity();
        if (bestSimilarity < appProperties.getAppHelp().getMinSimilarity()) {
            log.info("App-help retrieval below similarity threshold for query '{}': {}", query, bestSimilarity);
            return null;
        }

        String context = matches.stream()
                .map(match -> "[" + match.title() + "]\n" + match.content())
                .collect(Collectors.joining("\n\n"));

        String prompt = "You are the in-app help assistant for a personal finance application.\n\n" +
                "Use only the retrieved product context below to answer the user's question.\n" +
                "If the user says alerts, map that to anomalies or unusual activity if supported by the context.\n" +
                "Do not invent pages, actions, filters, or workflows that are not in the context.\n" +
                "If the context is insufficient, reply exactly: I couldn't map that question to a supported app feature.\n" +
                "Keep the answer concise and product-focused.\n\n" +
                "User question:\n" + query + "\n\n" +
                "Retrieved context:\n" + context;

        return llmService.generate(prompt).trim();
    }

    private List<RetrievedChunk> retrieve(String query) {
        ensureSchema();

        Integer totalChunks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_help_chunks WHERE source_document = :sourceDocument",
                new MapSqlParameterSource("sourceDocument", SOURCE_DOCUMENT),
                Integer.class
        );
        if (totalChunks == null || totalChunks == 0) {
            log.warn("App-help knowledge base is empty. Load the knowledge base before serving app-help queries.");
            return List.of();
        }

        List<Float> queryEmbedding = embed(expandQuery(query), "RETRIEVAL_QUERY", null);
        String queryVector = toVectorLiteral(queryEmbedding);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("sourceDocument", SOURCE_DOCUMENT)
                .addValue("queryEmbedding", queryVector)
                .addValue("limit", appProperties.getAppHelp().getTopK());

        return jdbcTemplate.query(
                "SELECT title, content, content_hash, " +
                        "1 - (embedding <=> CAST(:queryEmbedding AS vector)) AS similarity " +
                        "FROM app_help_chunks " +
                        "WHERE source_document = :sourceDocument " +
                        "ORDER BY embedding <=> CAST(:queryEmbedding AS vector) ASC " +
                        "LIMIT :limit",
                params,
                (rs, rowNum) -> new RetrievedChunk(
                        rs.getString("title"),
                        rs.getString("content"),
                        rs.getString("content_hash"),
                        rs.getDouble("similarity")
                )
        );
    }

    private void ensureSchema() {
        int dimension = appProperties.getAppHelp().getOutputDimensionality();
        jdbcTemplate.getJdbcTemplate().execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbcTemplate.getJdbcTemplate().execute(
                "CREATE TABLE IF NOT EXISTS app_help_chunks (" +
                        "id BIGSERIAL PRIMARY KEY, " +
                        "source_key VARCHAR(255) NOT NULL UNIQUE, " +
                        "source_document VARCHAR(255) NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "content TEXT NOT NULL, " +
                        "content_hash VARCHAR(64) NOT NULL, " +
                        "embedding vector(" + dimension + ") NOT NULL, " +
                        "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                        ")"
        );
        jdbcTemplate.getJdbcTemplate().execute(
                "CREATE INDEX IF NOT EXISTS idx_app_help_chunks_source_document ON app_help_chunks(source_document)"
        );
    }

    private Map<String, String> loadExistingHashes() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT source_key, content_hash FROM app_help_chunks WHERE source_document = :sourceDocument",
                new MapSqlParameterSource("sourceDocument", SOURCE_DOCUMENT)
        );

        Map<String, String> hashes = new HashMap<>();
        for (Map<String, Object> row : rows) {
            hashes.put((String) row.get("source_key"), (String) row.get("content_hash"));
        }
        return hashes;
    }

    private void upsertChunk(HelpChunk chunk, List<Float> embedding) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("sourceKey", chunk.sourceKey())
                .addValue("sourceDocument", SOURCE_DOCUMENT)
                .addValue("title", chunk.title())
                .addValue("content", chunk.content())
                .addValue("contentHash", chunk.contentHash())
                .addValue("embedding", toVectorLiteral(embedding))
                .addValue("updatedAt", Timestamp.from(Instant.now()));

        jdbcTemplate.update(
                "INSERT INTO app_help_chunks (source_key, source_document, title, content, content_hash, embedding, updated_at) " +
                        "VALUES (:sourceKey, :sourceDocument, :title, :content, :contentHash, CAST(:embedding AS vector), :updatedAt) " +
                        "ON CONFLICT (source_key) DO UPDATE SET " +
                        "title = EXCLUDED.title, " +
                        "content = EXCLUDED.content, " +
                        "content_hash = EXCLUDED.content_hash, " +
                        "embedding = EXCLUDED.embedding, " +
                        "updated_at = EXCLUDED.updated_at",
                params
        );
    }

    private int deleteStaleChunks(List<String> activeSourceKeys) {
        if (activeSourceKeys.isEmpty()) {
            return jdbcTemplate.update(
                    "DELETE FROM app_help_chunks WHERE source_document = :sourceDocument",
                    new MapSqlParameterSource("sourceDocument", SOURCE_DOCUMENT)
            );
        }

        return jdbcTemplate.update(
                "DELETE FROM app_help_chunks " +
                        "WHERE source_document = :sourceDocument " +
                        "AND source_key NOT IN (:activeSourceKeys)",
                new MapSqlParameterSource()
                        .addValue("sourceDocument", SOURCE_DOCUMENT)
                        .addValue("activeSourceKeys", activeSourceKeys)
        );
    }

    private List<Float> embed(String text, String taskType, String title) {
        AppProperties.Llm llm = appProperties.getLlm();
        if (llm.getProjectId() == null || llm.getProjectId().isBlank()) {
            throw new IllegalStateException("LLM project ID is not configured");
        }
        if (llm.getLocation() == null || llm.getLocation().isBlank()) {
            throw new IllegalStateException("LLM location is not configured");
        }

        EmbedContentConfig.Builder configBuilder = EmbedContentConfig.builder()
                .taskType(taskType)
                .outputDimensionality(appProperties.getAppHelp().getOutputDimensionality());

        if (title != null && !title.isBlank()) {
            configBuilder.title(title);
        }

        try (Client client = Client.builder()
                .vertexAI(true)
                .project(llm.getProjectId())
                .location(llm.getLocation())
                .build()) {
            EmbedContentResponse response = client.models.embedContent(
                    appProperties.getAppHelp().getEmbeddingModel(),
                    text,
                    configBuilder.build()
            );

            Optional<List<ContentEmbedding>> embeddings = response.embeddings();
            if (embeddings.isEmpty() || embeddings.get().isEmpty()) {
                throw new IllegalStateException("Embedding response did not contain embeddings");
            }

            Optional<List<Float>> values = embeddings.get().get(0).values();
            if (values.isEmpty() || values.get().isEmpty()) {
                throw new IllegalStateException("Embedding response did not contain values");
            }

            return values.get();
        } catch (Exception e) {
            log.error("Failed to create embedding for app-help text", e);
            throw new IllegalStateException("Failed to create app-help embeddings", e);
        }
    }

    private Path resolveKnowledgeBasePath() {
        return Path.of(appProperties.getAppHelp().getKnowledgeBasePath());
    }

    private void validateKnowledgeBasePath(Path path) {
        if (!Files.exists(path)) {
            log.error("App-help knowledge base file not found at {}", path.toAbsolutePath());
            throw new IllegalStateException("Knowledge base file not found: " + path.toAbsolutePath());
        }
    }

    private List<HelpChunk> parseKnowledgeBase(Path path) {
        validateKnowledgeBasePath(path);

        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            List<HelpChunk> chunks = new ArrayList<>();
            String h1 = "";
            String h2 = "";
            String h3 = "";
            StringBuilder block = new StringBuilder();
            boolean inCodeBlock = false;

            for (String rawLine : lines) {
                String line = rawLine.stripTrailing();

                if (line.startsWith("```")) {
                    inCodeBlock = !inCodeBlock;
                    block.append(line.trim()).append('\n');
                    continue;
                }

                if (!inCodeBlock && line.startsWith("# ")) {
                    flushChunk(chunks, h1, h2, h3, block);
                    h1 = cleanHeading(line.substring(2));
                    h2 = "";
                    h3 = "";
                    continue;
                }

                if (!inCodeBlock && line.startsWith("## ")) {
                    flushChunk(chunks, h1, h2, h3, block);
                    h2 = cleanHeading(line.substring(3));
                    h3 = "";
                    continue;
                }

                if (!inCodeBlock && line.startsWith("### ")) {
                    flushChunk(chunks, h1, h2, h3, block);
                    h3 = cleanHeading(line.substring(4));
                    continue;
                }

                if (line.isBlank()) {
                    flushChunk(chunks, h1, h2, h3, block);
                    continue;
                }

                block.append(line.trim()).append('\n');
            }

            flushChunk(chunks, h1, h2, h3, block);
            if (chunks.isEmpty()) {
                throw new IllegalStateException("Knowledge base document did not produce any chunks: " + path.toAbsolutePath());
            }
            return chunks;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read knowledge base file: " + path.toAbsolutePath(), e);
        }
    }

    private void flushChunk(List<HelpChunk> chunks, String h1, String h2, String h3, StringBuilder block) {
        if (block.length() == 0) {
            return;
        }

        String content = block.toString().trim();
        block.setLength(0);
        if (content.length() < 20) {
            return;
        }

        String title = List.of(h1, h2, h3).stream()
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" > "));

        String sourceKey = slugify(title + "-" + Math.abs(content.hashCode()));
        String contentHash = sha256(title + "\n" + content);
        String embeddingText = title.isBlank() ? content : title + "\n" + content;
        chunks.add(new HelpChunk(sourceKey, title, content, contentHash, embeddingText));
    }

    private String expandQuery(String query) {
        String normalized = normalize(query);
        StringBuilder expanded = new StringBuilder(normalized);

        if (containsAny(normalized, "alert", "alerts", "warning", "warnings")) {
            expanded.append(" anomalies anomaly unusual activity");
        }
        if (containsAny(normalized, "analytics", "trend", "trends", "breakdown", "explorer")) {
            expanded.append(" analytics summary entities categories");
        }
        if (containsAny(normalized, "upload", "import", "file", "files", "statement", "statements", "csv")) {
            expanded.append(" ingestion jobs upload transactions");
        }
        if (containsAny(normalized, "assistant", "chat", "query", "history")) {
            expanded.append(" query workspace clarification raw result");
        }
        if (containsAny(normalized, "mapping", "taxonomy", "settings", "profile")) {
            expanded.append(" settings category mappings profile");
        }

        return expanded.toString();
    }

    private boolean containsAny(String query, String... tokens) {
        return Arrays.stream(tokens).anyMatch(query::contains);
    }

    private String cleanHeading(String heading) {
        return heading.replace("`", "").trim();
    }

    private String slugify(String input) {
        return normalize(input).replace(' ', '-');
    }

    private String normalize(String input) {
        if (input == null) {
            return "";
        }
        return NON_ALNUM.matcher(input.toLowerCase()).replaceAll(" ").trim().replaceAll("\\s+", " ");
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String toVectorLiteral(List<Float> values) {
        return values.stream()
                .map(value -> Float.isFinite(value) ? value.toString() : "0.0")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private record HelpChunk(String sourceKey,
                             String title,
                             String content,
                             String contentHash,
                             String embeddingText) {
    }

    private record RetrievedChunk(String title,
                                  String content,
                                  String contentHash,
                                  double similarity) {
    }

    private enum LoadState {
        IDLE,
        RUNNING,
        COMPLETED,
        FAILED
    }

    private static final class LoadStatus {
        private final LoadState state;
        private final String sourcePath;
        private final String documentName;
        private final int totalChunks;
        private final int processedChunks;
        private final int createdChunks;
        private final int updatedChunks;
        private final int unchangedChunks;
        private final int deletedChunks;
        private final Instant startedAt;
        private final Instant completedAt;
        private final String errorMessage;

        private LoadStatus(LoadState state,
                           String sourcePath,
                           String documentName,
                           int totalChunks,
                           int processedChunks,
                           int createdChunks,
                           int updatedChunks,
                           int unchangedChunks,
                           int deletedChunks,
                           Instant startedAt,
                           Instant completedAt,
                           String errorMessage) {
            this.state = state;
            this.sourcePath = sourcePath;
            this.documentName = documentName;
            this.totalChunks = totalChunks;
            this.processedChunks = processedChunks;
            this.createdChunks = createdChunks;
            this.updatedChunks = updatedChunks;
            this.unchangedChunks = unchangedChunks;
            this.deletedChunks = deletedChunks;
            this.startedAt = startedAt;
            this.completedAt = completedAt;
            this.errorMessage = errorMessage;
        }

        private static LoadStatus idle() {
            return new LoadStatus(LoadState.IDLE, null, SOURCE_DOCUMENT, 0, 0, 0, 0, 0, 0, null, null, null);
        }

        private static LoadStatus started(String sourcePath, String documentName) {
            return new LoadStatus(LoadState.RUNNING, sourcePath, documentName, 0, 0, 0, 0, 0, 0, Instant.now(), null, null);
        }

        private LoadStatus withTotals(int totalChunks) {
            return new LoadStatus(state, sourcePath, documentName, totalChunks, processedChunks, createdChunks, updatedChunks,
                    unchangedChunks, deletedChunks, startedAt, completedAt, errorMessage);
        }

        private LoadStatus progress(int processedChunks,
                                    int createdChunks,
                                    int updatedChunks,
                                    int unchangedChunks,
                                    int deletedChunks) {
            return new LoadStatus(LoadState.RUNNING, sourcePath, documentName, totalChunks, processedChunks, createdChunks,
                    updatedChunks, unchangedChunks, deletedChunks, startedAt, null, null);
        }

        private LoadStatus completed(int createdChunks,
                                     int updatedChunks,
                                     int unchangedChunks,
                                     int deletedChunks) {
            return new LoadStatus(LoadState.COMPLETED, sourcePath, documentName, totalChunks, totalChunks, createdChunks,
                    updatedChunks, unchangedChunks, deletedChunks, startedAt, Instant.now(), null);
        }

        private LoadStatus failed(String errorMessage) {
            return new LoadStatus(LoadState.FAILED, sourcePath, documentName, totalChunks, processedChunks, createdChunks,
                    updatedChunks, unchangedChunks, deletedChunks, startedAt, Instant.now(), errorMessage);
        }

        private AppHelpLoadResponse toResponse() {
            double percentComplete = totalChunks <= 0 ? 0.0d : (processedChunks * 100.0d) / totalChunks;
            return AppHelpLoadResponse.builder()
                    .state(state.name())
                    .sourcePath(sourcePath)
                    .documentName(documentName)
                    .totalChunks(totalChunks)
                    .processedChunks(processedChunks)
                    .createdChunks(createdChunks)
                    .updatedChunks(updatedChunks)
                    .unchangedChunks(unchangedChunks)
                    .deletedChunks(deletedChunks)
                    .percentComplete(percentComplete)
                    .startedAt(startedAt)
                    .completedAt(completedAt)
                    .errorMessage(errorMessage)
                    .build();
        }
    }
}
