package com.prashant.springai.rag.service.ai;

import com.prashant.springai.rag.config.CacheConfig;
import com.prashant.springai.rag.model.AgentClassificationResult;
import com.prashant.springai.rag.model.AgentIntent;
import com.prashant.springai.rag.model.AgentRoute;
import com.prashant.springai.rag.utils.AgentIntentHeuristicResolver;
import com.prashant.springai.rag.utils.AgentRouteHeuristicResolver;
import com.prashant.springai.rag.utils.PromptReaderUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
@AllArgsConstructor
public class AgentClassificationService {

  private static final String ROUTE_CLASSIFIER_PROMPT_PATH = "classpath:prompts/user-prompts/agent-route-classifier.txt";
  private static final String CLASSIFIER_PROMPT_VERSION = "v1";

  private final MultiModelProviderService multiModelProviderService;
  private final ResourceLoader resourceLoader;
  private final CacheManager cacheManager;

  public AgentClassificationResult classify(String question, String orderNumber, String aiProvider) {
    String cacheKey = buildClassificationCacheKey(question, orderNumber, aiProvider);
    Cache cache = cacheManager.getCache(CacheConfig.INTENT_CLASSIFICATION_CACHE);
    if (cache != null) {
      AgentClassificationResult cached = cache.get(cacheKey, AgentClassificationResult.class);
      if (cached != null) {
        log.info("[{}] HIT key={} -> returning cached classification (LLM not invoked)",
          CacheConfig.INTENT_CLASSIFICATION_CACHE, cacheKey);
        return cached;
      }
      log.info("[{}] MISS key={} -> invoking classifier",
        CacheConfig.INTENT_CLASSIFICATION_CACHE, cacheKey);
    }

    AgentClassificationResult llmClassification = tryClassifyWithLlm(question, orderNumber, aiProvider);
    AgentClassificationResult result;
    if (isCompleteClassification(llmClassification)) {
      log.info("LLM classifier selected route={}, intent={}", llmClassification.route(), llmClassification.intent());
      result = llmClassification;
    } else {
      result = classifyWithHeuristics(question, orderNumber);
    }

    if (cache != null && result != null) {
      cache.put(cacheKey, result);
      log.info("[{}] STORE key={}", CacheConfig.INTENT_CLASSIFICATION_CACHE, cacheKey);
    }
    return result;
  }

  public String buildClassificationCacheKey(String question, String orderNumber, String aiProvider) {
    String normalizedQuestion = normalizeQuestion(question);
    String normalizedOrderNumber = normalizeOrderNumber(orderNumber);
    String normalizedProvider = normalizeProvider(aiProvider);
    return String.join("|", normalizedProvider, normalizedOrderNumber, CLASSIFIER_PROMPT_VERSION, normalizedQuestion);
  }

  private AgentClassificationResult tryClassifyWithLlm(String question, String orderNumber, String aiProvider) {
    try {
      return multiModelProviderService.executeWithTimeoutOrThrow(
        "agent route and intent classification",
        () -> multiModelProviderService.getChatClient(aiProvider)
          .prompt()
          .user(buildClassifierPrompt(question, orderNumber))
          .call()
          .entity(AgentClassificationResult.class)
      );
    } catch (Exception ex) {
      log.warn("LLM route+intent classification failed. Falling back to heuristic resolver.", ex);
      return null;
    }
  }

  private boolean isCompleteClassification(AgentClassificationResult classification) {
    boolean isComplete = classification != null && classification.route() != null && classification.intent() != null;
    if (!isComplete) {
      log.warn("LLM classifier returned incomplete classification. classification={}", classification);
    }
    return isComplete;
  }

  private AgentClassificationResult classifyWithHeuristics(String question, String orderNumber) {
    AgentRoute fallbackRoute = AgentRouteHeuristicResolver.resolve(question, orderNumber);
    AgentIntent fallbackIntent = AgentIntentHeuristicResolver.resolve(question, fallbackRoute);
    log.info("Heuristic classifier selected route={}, intent={}", fallbackRoute, fallbackIntent);
    return new AgentClassificationResult(fallbackRoute, fallbackIntent);
  }

  private String buildClassifierPrompt(String question, String orderNumber) {
    String promptTemplate = PromptReaderUtil.getPrompt(resourceLoader, ROUTE_CLASSIFIER_PROMPT_PATH);
    PromptTemplate template = new PromptTemplate(promptTemplate);
    Map<String, Object> substitutionVariables = Map.of(
      "QUESTION", question == null ? "" : question,
      "ORDER_NUMBER", orderNumber == null ? "" : orderNumber
    );
    return template.create(substitutionVariables).getContents();
  }

  private String normalizeQuestion(String question) {
    if (!StringUtils.hasText(question)) {
      return "";
    }
    return question.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  private String normalizeOrderNumber(String orderNumber) {
    if (!StringUtils.hasText(orderNumber)) {
      return "NONE";
    }
    return orderNumber.trim().toUpperCase(Locale.ROOT);
  }

  private String normalizeProvider(String aiProvider) {
    return Objects.requireNonNullElse(aiProvider, "").trim().toLowerCase(Locale.ROOT);
  }
}
