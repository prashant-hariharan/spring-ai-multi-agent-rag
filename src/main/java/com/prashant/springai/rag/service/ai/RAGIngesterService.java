package com.prashant.springai.rag.service.ai;

import com.prashant.springai.rag.config.CacheConfig;
import com.prashant.springai.rag.model.RagDocumentCatalog;
import com.prashant.springai.rag.model.RagDocumentType;
import com.prashant.springai.rag.repository.RagDocumentCatalogRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
@AllArgsConstructor
public class RAGIngesterService {

  private static final int CHUNK_SIZE = 500;
  private static final int MIN_CHUNK_SIZE_CHARS = 350;
  private static final int MIN_CHUNK_LENGTH_TO_EMBED = 5;
  private static final int MAX_NUM_CHUNKS = 10000;
  private static final boolean KEEP_SEPARATOR = true;

  private final VectorStore vectorStore;
  private final RagDocumentCatalogRepository ragDocumentCatalogRepository;

  @CacheEvict(
    cacheNames = {CacheConfig.RAG_QUERY_RESPONSE_CACHE, CacheConfig.RAG_CONTEXT_CACHE},
    allEntries = true
  )
  public int loadAndIndexDocumentFromString(String content, String filename, RagDocumentType documentType) {
    try {
      log.info("Indexing document: {}", filename);
      String normalizedFileName = normalizeFileName(filename);
      RagDocumentType normalizedDocumentType = Objects.requireNonNull(documentType, "Document type is required.");
      replaceExistingEmbeddingsForFile(normalizedFileName);
      upsertDocumentCatalog(normalizedFileName, normalizedDocumentType);

      Document document = new Document(
        content,
        buildMetadata(normalizedFileName, "classpath", null, normalizedDocumentType)
      );
      return splitAndIndex(List.of(document), normalizedFileName);

    } catch (Exception e) {
      log.error("Error indexing document: {}", filename, e);
      throw new RuntimeException("Failed to index document: " + e.getMessage(), e);
    }
  }

  private String normalizeFileName(String fileName) {
    if (!StringUtils.hasText(fileName)) {
      throw new IllegalArgumentException("File name is required for indexing.");
    }
    return fileName.trim();
  }

  private void replaceExistingEmbeddingsForFile(String fileName) {
    String filterExpression = "fileName == '" + escapeFilterValue(fileName) + "'";
    log.info("Removing existing embeddings for fileName={}", fileName);
    vectorStore.delete(filterExpression);
  }

  private String escapeFilterValue(String value) {
    return value.replace("'", "\\'");
  }

  private void upsertDocumentCatalog(String fileName, RagDocumentType documentType) {
    RagDocumentCatalog catalogRecord = ragDocumentCatalogRepository.findByFileNameAndDocumentType(fileName, documentType)
      .orElseGet(RagDocumentCatalog::new);
    catalogRecord.setFileName(fileName);
    catalogRecord.setDocumentType(documentType);
    catalogRecord.setSourceSystem("classpath");
    catalogRecord.setIndexedAt(Instant.now());
    ragDocumentCatalogRepository.save(catalogRecord);
  }

  private Map<String, Object> buildMetadata(
    String fileName,
    String sourceSystem,
    String sourcePath,
    RagDocumentType documentType
  ) {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("fileName", fileName);
    metadata.put("documentType", documentType.name());
    metadata.put("sourceSystem", sourceSystem);
    metadata.put("indexedAt", Instant.now().toString());
    if (StringUtils.hasText(sourcePath)) {
      metadata.put("source_path", sourcePath);
    }
    return metadata;
  }

  private int splitAndIndex(List<Document> documents, String sourceLabel) {
    List<Document> chunks = createSplitter().apply(documents);

    log.info("Split into {} chunks", chunks.size());

    vectorStore.add(chunks);
    log.info("Indexed {} chunks from {}", chunks.size(), sourceLabel);
    return chunks.size();
  }

  private TokenTextSplitter createSplitter() {
    return new TokenTextSplitter(
      CHUNK_SIZE,
      MIN_CHUNK_SIZE_CHARS,
      MIN_CHUNK_LENGTH_TO_EMBED,
      MAX_NUM_CHUNKS,
      KEEP_SEPARATOR
    );
  }
}
