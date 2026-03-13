package com.prashant.springai.rag.service.ai;

import com.prashant.springai.rag.config.CacheConfig;
import com.prashant.springai.rag.model.AgentIntent;
import com.prashant.springai.rag.model.RagDocumentCatalog;
import com.prashant.springai.rag.model.RagDocumentType;
import com.prashant.springai.rag.repository.RagDocumentCatalogRepository;
import com.prashant.springai.rag.utils.PromptReaderUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RAGQueryService {
  private static final String RAG_QUERY_USER_PROMPT_PATH =
    "classpath:prompts/user-prompts/rag-query-user.txt";
  private static final String RAG_PROMPT_VERSION = "v1";
  private static final String RAG_CONTEXT_VERSION = "v1";
  private static final double NO_THRESHOLD = 0.0;

  private final MultiModelProviderService multiModelProviderService;
  private final VectorStore vectorStore;
  private final ResourceLoader resourceLoader;
  private final CacheManager cacheManager;
  private final RagDocumentCatalogRepository ragDocumentCatalogRepository;
  @Value("${app.rag.retrieval.default-top-k:3}")
  private int defaultTopK;
  @Value("${app.rag.retrieval.combined-top-k:5}")
  private int combinedTopK;
  @Value("${app.rag.retrieval.similarity-threshold:0.35}")
  private double similarityThreshold;

  public String askQuestion(String question, String aiProvider, Collection<String> fileNames) {
    List<String> resolvedFileNames = resolveEffectiveFileNames(fileNames, null);
    String cacheKey = buildRagQueryCacheKey(question, aiProvider, resolvedFileNames);
    Cache cache = cacheManager.getCache(CacheConfig.RAG_QUERY_RESPONSE_CACHE);
    if (cache != null) {
      String cached = cache.get(cacheKey, String.class);
      if (cached != null) {
        log.info("[{}] HIT key={} -> returning cached response (LLM not invoked)",
          CacheConfig.RAG_QUERY_RESPONSE_CACHE, cacheKey);
        return cached;
      }
      log.info("[{}] MISS key={} -> running retrieval + LLM",
        CacheConfig.RAG_QUERY_RESPONSE_CACHE, cacheKey);
    }

    String response = generateRagAnswer(question, aiProvider, resolvedFileNames);
    if (cache != null && shouldCache(response)) {
      cache.put(cacheKey, response);
      log.info("[{}] STORE key={}", CacheConfig.RAG_QUERY_RESPONSE_CACHE, cacheKey);
    } else if (cache != null) {
      log.info("[{}] SKIP-STORE key={} -> fallback/error response", CacheConfig.RAG_QUERY_RESPONSE_CACHE, cacheKey);
    }
    return response;
  }

  public String askQuestionWithAgentIntent(String question, String aiProvider, AgentIntent intent) {
    List<String> resolvedFileNames = resolveEffectiveFileNames(Collections.emptyList(), intent);
    return askQuestion(question, aiProvider, resolvedFileNames);
  }

  public String fetchRelevantContext(String question, Collection<String> fileNames) {
    if (!StringUtils.hasText(question)) {
      return "";
    }
    List<String> resolvedFileNames = resolveEffectiveFileNames(fileNames, null);
    String cacheKey = buildRagContextCacheKey(question, resolvedFileNames);
    Cache cache = cacheManager.getCache(CacheConfig.RAG_CONTEXT_CACHE);
    if (cache != null) {
      String cached = cache.get(cacheKey, String.class);
      if (cached != null) {
        log.info("[{}] HIT key={} -> returning cached context (retrieval not invoked)",
          CacheConfig.RAG_CONTEXT_CACHE, cacheKey);
        return cached;
      }
      log.info("[{}] MISS key={} -> running retrieval",
        CacheConfig.RAG_CONTEXT_CACHE, cacheKey);
    }

    String context = fetchRelevantContextInternal(question, resolvedFileNames);
    if (cache != null && StringUtils.hasText(context)) {
      cache.put(cacheKey, context);
      log.info("[{}] STORE key={}", CacheConfig.RAG_CONTEXT_CACHE, cacheKey);
    } else if (cache != null) {
      log.info("[{}] SKIP-STORE key={} -> empty context", CacheConfig.RAG_CONTEXT_CACHE, cacheKey);
    }
    return context;
  }

  public String fetchRelevantContextWithAgentIntent(String question, AgentIntent intent) {
    List<String> resolvedFileNames = resolveEffectiveFileNames(Collections.emptyList(), intent);
    return fetchRelevantContext(question, resolvedFileNames);
  }

  public String buildRagQueryCacheKeyFromFileName(String question, String aiProvider, String fileName) {
    List<String> fileNames = StringUtils.hasText(fileName) ? List.of(fileName) : Collections.emptyList();
    return buildRagQueryCacheKey(question, aiProvider, fileNames);
  }

  public String buildRagQueryCacheKey(String question, String aiProvider, Collection<String> fileNames) {
    String normalizedProvider = normalizeProvider(aiProvider);
    String normalizedQuestion = normalizeQuestion(question);
    String normalizedScope = normalizeScope(fileNames);
    return String.join("|", normalizedProvider, normalizedScope, RAG_PROMPT_VERSION, normalizedQuestion);
  }

  public String buildRagContextCacheKey(String question, Collection<String> fileNames) {
    String normalizedQuestion = normalizeQuestion(question);
    String normalizedScope = normalizeScope(fileNames);
    return String.join("|", normalizedScope, RAG_CONTEXT_VERSION, normalizedQuestion);
  }

  private String generateRagAnswer(String question, String aiProvider, Collection<String> fileNames) {
    log.info("RAG question: {}", question);

    if (!StringUtils.hasText(question)) {
      return "Please provide a question.";
    }

    try {
      List<Document> relevantDocs = retrieveRelevantDocs(question, fileNames, defaultTopK, similarityThreshold);

      if (relevantDocs.isEmpty()) {
        log.warn("No relevant documents found");
        return "I don't have information about that in my knowledge base.";
      }

      log.info("Found {} relevant document(s)", relevantDocs.size());

      String context = relevantDocs.stream()
        .map(Document::getFormattedContent)
        .collect(Collectors.joining("\n\n"));

      String userPromptTemplate = PromptReaderUtil.getPrompt(resourceLoader, RAG_QUERY_USER_PROMPT_PATH);
      String userPrompt = userPromptTemplate.formatted(question, context);

      String answer = multiModelProviderService.executeWithTimeoutOrFallback(
        "RAG query answer generation",
        () -> multiModelProviderService.getChatClient(aiProvider)
          .prompt()
          .user(userPrompt)
          .call()
          .content(),
        "Something went wrong while generating the response. Please try again."
      );

      log.info("Answer generated successfully");
      return answer;

    } catch (NonTransientAiException e) {
      throw e;
    } catch (Exception e) {
      log.error("Error during RAG query", e);
      return "I'm unable to answer that question right now.";
    }
  }

  private String fetchRelevantContextInternal(String question, Collection<String> fileNames) {
    try {
      List<Document> relevantDocs = retrieveRelevantDocs(question, fileNames, combinedTopK, similarityThreshold);
      if (relevantDocs.isEmpty()) {
        return "";
      }
      return relevantDocs.stream()
        .map(Document::getFormattedContent)
        .collect(Collectors.joining("\n\n"));
    } catch (NonTransientAiException ex) {
      throw ex;
    } catch (Exception ex) {
      log.error("Error retrieving RAG context for combined flow", ex);
      return "";
    }
  }

  private List<Document> retrieveRelevantDocs(
    String question,
    Collection<String> fileNames,
    int topK,
    double similarityThreshold
  ) {
    SearchRequest.Builder searchRequestBuilder = SearchRequest.builder()
      .query(question)
      .topK(topK);

    String filterExpression = buildFilterExpression(fileNames);
    if (StringUtils.hasText(filterExpression)) {
      log.info("Applying metadata filter for retrieval: {}", filterExpression);
      searchRequestBuilder.filterExpression(filterExpression);
    }

    if (similarityThreshold > NO_THRESHOLD) {
      searchRequestBuilder.similarityThreshold(similarityThreshold);
    }
    List<Document> docs = vectorStore.similaritySearch(searchRequestBuilder.build());
    if (!docs.isEmpty() || similarityThreshold <= NO_THRESHOLD) {
      return docs;
    }

    log.warn("No docs found with similarityThreshold={}, retrying without threshold", similarityThreshold);
    SearchRequest.Builder retryBuilder = SearchRequest.builder()
      .query(question)
      .topK(topK);
    if (StringUtils.hasText(filterExpression)) {
      retryBuilder.filterExpression(filterExpression);
    }
    return vectorStore.similaritySearch(retryBuilder.build());
  }

  private String buildFilterExpression(Collection<String> fileNames) {
    if (fileNames == null || fileNames.isEmpty()) {
      return null;
    }

    List<String> normalizedFileNames = new ArrayList<>();
    for (String fileName : fileNames) {
      if (StringUtils.hasText(fileName)) {
        normalizedFileNames.add(fileName.trim());
      }
    }

    if (normalizedFileNames.isEmpty()) {
      return null;
    }

    StringJoiner fileFilters = new StringJoiner(" || ");
    for (String fileName : normalizedFileNames) {
      fileFilters.add("fileName == '" + escapeFilterValue(fileName) + "'");
    }
    return "(" + fileFilters + ")";
  }

  private List<String> resolveEffectiveFileNames(Collection<String> explicitFileNames, AgentIntent intent) {
    List<String> normalizedExplicit = normalizeFileNames(explicitFileNames);
    if (!normalizedExplicit.isEmpty()) {
      log.debug("Resolved RAG file scope from explicit input: {}", normalizedExplicit);
      return normalizedExplicit;
    }
    //if explicit filenames are not provided and agent intent is provided
    List<RagDocumentType> documentTypes = mapIntentToDocumentTypes(intent);
    List<RagDocumentCatalog> catalogRecords = documentTypes.isEmpty()
      ? ragDocumentCatalogRepository.findAll()
      : ragDocumentCatalogRepository.findAllByDocumentTypeIn(documentTypes);

    List<String> catalogFileNames = normalizeFileNames(
      catalogRecords.stream().map(RagDocumentCatalog::getFileName).toList()
    );

    if (!catalogFileNames.isEmpty()) {
      log.info("Resolved RAG file scope from catalog for intent={} and documentTypes={}: {}",
        intent, documentTypes, catalogFileNames);
      return catalogFileNames;
    }

    log.info("No file scope derived from catalog for intent={} and documentTypes={}. Falling back to unfiltered retrieval.",
      intent, documentTypes);
    return Collections.emptyList();
  }

  private List<RagDocumentType> mapIntentToDocumentTypes(AgentIntent intent) {
    if (intent == null) {
      return Collections.emptyList();
    }
    return switch (intent) {
      case EMPLOYEE_HANDBOOK -> List.of(RagDocumentType.EMPLOYEE);
      case POLICY_AND_EMPLOYEE -> List.of(RagDocumentType.POLICY, RagDocumentType.EMPLOYEE);
      case POLICY -> List.of(RagDocumentType.POLICY);
      case NONE -> Arrays.asList(RagDocumentType.values());
    };
  }

  private List<String> normalizeFileNames(Collection<String> fileNames) {
    if (fileNames == null || fileNames.isEmpty()) {
      return Collections.emptyList();
    }
    return fileNames.stream()
      .filter(StringUtils::hasText)
      .map(String::trim)
      .distinct()
      .sorted()
      .toList();
  }

  private String escapeFilterValue(String value) {
    return value.replace("'", "\\'");
  }

  private String normalizeProvider(String aiProvider) {
    return Objects.requireNonNullElse(aiProvider, "").trim().toLowerCase(Locale.ROOT);
  }

  private String normalizeQuestion(String question) {
    if (!StringUtils.hasText(question)) {
      return "";
    }
    return question.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  private String normalizeScope(Collection<String> fileNames) {
    if (fileNames == null || fileNames.isEmpty()) {
      return "ALL";
    }
    List<String> normalized = fileNames.stream()
      .filter(StringUtils::hasText)
      .map(name -> name.trim().toLowerCase(Locale.ROOT))
      .distinct()
      .sorted()
      .toList();
    if (normalized.isEmpty()) {
      return "ALL";
    }
    return String.join(",", normalized);
  }

  private boolean shouldCache(String response) {
    if (!StringUtils.hasText(response)) {
      return false;
    }
    String normalized = response.trim().toLowerCase(Locale.ROOT);
    return !normalized.contains("something went wrong")
      && !normalized.contains("don't have information")
      && !normalized.contains("unable to answer");
  }
}
