package com.example.client.storage;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.example.client.service.AzureEmbeddingService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Slf4j
public class InMemoryVectorStore {
    private static final int DEFAULT_MAX_VECTORS = 100000;
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.0;
    private static final int DEFAULT_EMBEDDING_DIMENSION = 3072;
    private static final int MAX_SEARCH_RESULTS = 1000;
    private static final int MAX_TEXT_LENGTH = 8192;
    private static final int MAX_QUERY_LENGTH = 1000;

    private static final String PERSISTENCE_FILE_DIR = "embedding";
    private static final String PERSISTENCE_FILENAME = "vector_store.bin";
    private static final boolean ENABLE_COMPRESSION = true;

    private final EmbeddingModel azureEmbeddingModel;
    private final AzureEmbeddingService azureEmbeddingService;

    private final List<float[]> vectors = new ArrayList<>();
    private final List<Map<String, Object>> metadata = new ArrayList<>();
    private final List<String> originalTexts = new ArrayList<>();
    private final List<LocalDateTime> timestamps = new ArrayList<>();
    private final Map<String, Integer> documentIndex = new ConcurrentHashMap<>();

    private final int maxVectorSize;
    private final double similarityThreshold;
    private final int embeddingDimension;
    private final Path runtimePersistencePath;

    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final ExecutorService executorService =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    private final AtomicInteger totalAdditions = new AtomicInteger(0);

    public InMemoryVectorStore(EmbeddingModel azureEmbeddingModel,
            AzureEmbeddingService azureEmbeddingService) {
        this(azureEmbeddingModel, azureEmbeddingService, DEFAULT_MAX_VECTORS,
                DEFAULT_SIMILARITY_THRESHOLD, DEFAULT_EMBEDDING_DIMENSION);
    }

    public InMemoryVectorStore(EmbeddingModel azureEmbeddingModel,
            AzureEmbeddingService azureEmbeddingService, int maxVectorSize,
            double similarityThreshold, int embeddingDimension) {
        this.azureEmbeddingModel = azureEmbeddingModel;
        this.azureEmbeddingService = azureEmbeddingService;
        this.maxVectorSize = maxVectorSize;
        this.similarityThreshold = similarityThreshold;
        this.embeddingDimension = embeddingDimension;

        // This path is relative to the application's launch directory, making it portable.
        this.runtimePersistencePath =
                Paths.get(System.getProperty("user.dir"), PERSISTENCE_FILE_DIR,
                        PERSISTENCE_FILENAME).toAbsolutePath();

        log.info("InMemoryVectorStore initialized - Max vectors: {}, Dimension: {}",
                this.maxVectorSize, this.embeddingDimension);
        log.info("Runtime vector store path set to: {}", this.runtimePersistencePath);
    }

    public void add(String text, Map<String, Object> meta) {
        if (!isValidInput(text, meta)) {
            log.warn("Invalid input provided to vector store. Skipping.");
            return;
        }

        rwLock.writeLock().lock();
        try {
            if (vectors.size() >= maxVectorSize) {
                performCapacityManagement();
            }

            float[] vector = computeEmbedding(text);
            if (isZeroVector(vector)) {
                log.error("Failed to compute valid embedding for text. Skipping.");
                return;
            }

            Map<String, Object> newMeta = new HashMap<>(meta);
            newMeta.put("addedAt", LocalDateTime.now());

            int currentIndex = vectors.size();
            vectors.add(vector);
            metadata.add(newMeta);
            originalTexts.add(text);
            timestamps.add(LocalDateTime.now());

            String docId = (String) newMeta.get("id");
            if (docId != null && !docId.isBlank()) {
                documentIndex.put(docId, currentIndex);
            }

            totalAdditions.incrementAndGet();
            log.debug("Added vector for ID '{}' at index {}. Total vectors: {}", docId,
                    currentIndex, vectors.size());

        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public boolean existsById(String uniqueId) {
        if (uniqueId == null || uniqueId.isBlank()) {
            return false;
        }
        rwLock.readLock().lock();
        try {
            return documentIndex.containsKey(uniqueId);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<Map<String, Object>> search(String query, int k) {
        if (isInvalidQuery(query, k))
            return Collections.emptyList();

        rwLock.readLock().lock();
        try {
            if (vectors.isEmpty())
                return Collections.emptyList();

            float[] queryVector = computeEmbedding(query);
            if (isZeroVector(queryVector))
                return Collections.emptyList();

            return IntStream.range(0, vectors.size()).parallel().mapToObj(
                            i -> new SearchResult(i, cosineSimilarity(queryVector, vectors.get(i))))
                    .filter(res -> res.similarity >= similarityThreshold)
                    .sorted(Comparator.comparingDouble(SearchResult::similarity).reversed())
                    .limit(Math.min(k, MAX_SEARCH_RESULTS)).map(this::createSearchResultMap)
                    .collect(Collectors.toList());

        } finally {
            rwLock.readLock().unlock();
        }
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        return (denominator == 0.0) ? 0.0 : dotProduct / denominator;
    }

    private Map<String, Object> createSearchResultMap(SearchResult result) {
        Map<String, Object> map = new HashMap<>();
        map.put("score", result.similarity);
        map.put("content", originalTexts.get(result.index));
        map.put("metadata", new HashMap<>(metadata.get(result.index)));
        return map;
    }

    private boolean isValidInput(String text, Map<String, Object> meta) {
        return text != null && !text.trim()
                .isEmpty() && text.length() <= MAX_TEXT_LENGTH && meta != null;
    }

    private boolean isInvalidQuery(String query, int k) {
        return query == null || query.trim()
                .isEmpty() || query.length() > MAX_QUERY_LENGTH || k <= 0;
    }

    private float[] computeEmbedding(String text) {
        try {
            if (azureEmbeddingService != null) {
                float[] embedding = azureEmbeddingService.generateEmbedding(text);
                if (embedding != null && !isZeroVector(embedding)) {
                    return embedding;
                }
                log.warn("AzureEmbeddingService returned a null or zero vector. Falling back.");
            }

            if (azureEmbeddingModel != null) {
                float[] embedding = azureEmbeddingModel.embed(text);
                if (embedding != null && !isZeroVector(embedding)) {
                    return embedding;
                }
            }
        } catch (Exception e) {
            log.error("Exception during embedding generation for text: '{}'",
                    text.substring(0, Math.min(50, text.length())), e);
        }

        log.warn("All embedding methods failed. Returning a zero vector.");
        return new float[embeddingDimension];
    }

    private boolean isZeroVector(float[] vector) {
        if (vector == null)
            return true;
        for (float v : vector) {
            if (v != 0.0f)
                return false;
        }
        return true;
    }

    private void performCapacityManagement() {
        int removeCount = vectors.size() - maxVectorSize + 1;
        if (removeCount <= 0)
            return;

        log.warn("Max vector capacity reached. Removing {} oldest vectors.", removeCount);
        List<Integer> indicesToRemove = IntStream.range(0, timestamps.size()).boxed()
                .sorted(Comparator.comparing(timestamps::get)).limit(removeCount)
                .sorted(Comparator.reverseOrder()).toList();

        for (int index : indicesToRemove) {
            removeVectorAt(index);
        }
        rebuildDocumentIndex();
    }

    private void removeVectorAt(int index) {
        vectors.remove(index);
        metadata.remove(index);
        originalTexts.remove(index);
        timestamps.remove(index);
    }

    private void rebuildDocumentIndex() {
        documentIndex.clear();
        for (int i = 0; i < metadata.size(); i++) {
            String id = (String) metadata.get(i).get("id");
            if (id != null) {
                documentIndex.put(id, i);
            }
        }
    }

    public void saveToFile() {
        rwLock.readLock().lock();
        try {
            log.info("Saving vector store with {} vectors to: {}", vectors.size(),
                    runtimePersistencePath);
            Files.createDirectories(runtimePersistencePath.getParent());

            VectorStoreData data =
                    new VectorStoreData(new ArrayList<>(vectors), new ArrayList<>(metadata),
                            new ArrayList<>(originalTexts), new ArrayList<>(timestamps),
                            new ConcurrentHashMap<>(documentIndex), totalAdditions.get());

            try (OutputStream fileOut = new FileOutputStream(runtimePersistencePath.toFile());
                    OutputStream gzipOut = ENABLE_COMPRESSION ?
                            new GZIPOutputStream(fileOut) :
                            fileOut;
                    Output output = new Output(gzipOut)) {
                createKryo().writeObject(output, data);
            }
            log.info("Successfully saved vector store.");
        } catch (Exception e) {
            throw new RuntimeException("Failed to save vector store to " + runtimePersistencePath,
                    e);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public boolean loadFromFile() {
        if (!Files.exists(runtimePersistencePath)) {
            log.info(
                    "Runtime persistence file not found at {}. Starting with a fresh vector store.",
                    runtimePersistencePath);
            return false;
        }

        log.info("Loading vector store from: {}", runtimePersistencePath);
        rwLock.writeLock().lock();
        try (InputStream fileIn = new FileInputStream(runtimePersistencePath.toFile());
                InputStream gzipIn = ENABLE_COMPRESSION ? new GZIPInputStream(fileIn) : fileIn;
                Input input = new Input(gzipIn)) {

            VectorStoreData data = createKryo().readObject(input, VectorStoreData.class);
            clearData();
            vectors.addAll(data.vectors());
            metadata.addAll(data.metadata());
            originalTexts.addAll(data.originalTexts());
            timestamps.addAll(data.timestamps());
            documentIndex.putAll(data.documentIndex());
            totalAdditions.set(data.totalAdditions());

            validateDataConsistency();
            log.info("Successfully loaded {} vectors from {}.", vectors.size(),
                    runtimePersistencePath);
            return true;
        } catch (Exception e) {
            log.error("Error loading vector store from {}. Starting fresh.", runtimePersistencePath,
                    e);
            clearData();
            return false;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private void clearData() {
        vectors.clear();
        metadata.clear();
        originalTexts.clear();
        timestamps.clear();
        documentIndex.clear();
        totalAdditions.set(0);
    }

    private void validateDataConsistency() {
        int size = vectors.size();
        if (metadata.size() != size || originalTexts.size() != size || timestamps.size() != size) {
            log.error(
                    "CRITICAL: Vector store data lists are inconsistent in size. This may cause instability.");
        }
    }

    private Kryo createKryo() {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        kryo.setReferences(true);
        return kryo;
    }

    public List<Map<String, Object>> searchByMetadata(String entityType, String fieldName,
            String fieldValue, int limit) {
        if (entityType == null || fieldName == null || fieldValue == null || limit <= 0)
            return Collections.emptyList();
        rwLock.readLock().lock();
        try {
            return IntStream.range(0, metadata.size()).filter(i -> {
                Map<String, Object> meta = metadata.get(i);
                return meta != null && entityType.equals(meta.get("type")) && fieldValue.equals(
                        String.valueOf(meta.get(fieldName)));
            }).limit(limit).mapToObj(i -> {
                Map<String, Object> result = new HashMap<>();
                result.put("content", originalTexts.get(i));
                result.put("metadata", new HashMap<>(metadata.get(i)));
                result.put("score", 1.0); // Exact metadata match
                result.put("matchType", "metadata");
                return result;
            }).collect(Collectors.toList());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public int size() {
        rwLock.readLock().lock();
        try {
            return vectors.size();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<Map<String, Object>> hybridSearch(String query, int limit) {
        if (isInvalidQuery(query, limit))
            return Collections.emptyList();

        List<Map<String, Object>> vectorResults = search(query, limit);
        List<Map<String, Object>> allResults = new ArrayList<>(vectorResults);
        String lowerQuery = query.toLowerCase();

        Set<String> potentialIds = new HashSet<>();
        Matcher matcher = Pattern.compile("\\b[a-zA-Z0-9-]{3,}\\b").matcher(query);
        while (matcher.find())
            potentialIds.add(matcher.group());

        for (String id : potentialIds) {
            allResults.addAll(searchById(id, 5));
        }

        Stream.of("student", "course", "research", "grade").filter(lowerQuery::contains)
                .forEach(type -> allResults.addAll(searchByEntityType(type, 5)));

        Map<String, Map<String, Object>> uniqueResults = new LinkedHashMap<>();
        for (Map<String, Object> result : allResults) {
            String key = generateResultKey(result);
            uniqueResults.merge(key, result, (oldV, newV) -> {
                Double oldS = (Double) oldV.getOrDefault("score", 0.0);
                Double newS = (Double) newV.getOrDefault("score", 0.0);
                return newS > oldS ? newV : oldV;
            });
        }

        return uniqueResults.values().stream()
                .sorted((a, b) -> Double.compare((Double) b.getOrDefault("score", 0.0),
                        (Double) a.getOrDefault("score", 0.0))).limit(limit)
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> searchByEntityType(String entityType, int limit) {
        return searchByMetadata(entityType, "type", entityType, limit);
    }

    private List<Map<String, Object>> searchById(String id, int limit) {
        List<Map<String, Object>> results = new ArrayList<>();
        rwLock.readLock().lock();
        try {
            Integer index = documentIndex.get(id);
            if (index != null && index < metadata.size()) {
                Map<String, Object> meta = metadata.get(index);
                if (id.equals(meta.get("id"))) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("content", originalTexts.get(index));
                    result.put("metadata", new HashMap<>(meta));
                    result.put("score", 1.0);
                    result.put("matchType", "id");
                    results.add(result);
                }
            }
        } finally {
            rwLock.readLock().unlock();
        }
        return results;
    }

    private String generateResultKey(Map<String, Object> result) {
        Map<String, Object> meta = (Map<String, Object>) result.get("metadata");
        if (meta != null) {
            String id = (String) meta.get("id");
            if (id != null)
                return id;
        }
        return "content:" + Objects.hash(result.get("content"));
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down InMemoryVectorStore...");
        saveToFile();

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("InMemoryVectorStore shut down successfully.");
    }

    private record SearchResult(int index, double similarity) {
    }


    private record VectorStoreData(List<float[]> vectors, List<Map<String, Object>> metadata,
                                   List<String> originalTexts, List<LocalDateTime> timestamps,
                                   Map<String, Integer> documentIndex, int totalAdditions)
            implements Serializable {
        @Serial
        private static final long serialVersionUID = 2L;
    }
}
