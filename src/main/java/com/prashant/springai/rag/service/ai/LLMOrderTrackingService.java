package com.prashant.springai.rag.service.ai;

import com.prashant.springai.rag.config.CacheConfig;
import com.prashant.springai.rag.config.ToolsFunctionConfig;
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

@Service
@Slf4j
@AllArgsConstructor
public class LLMOrderTrackingService {

  public static final String USER_MESSAGE = "USER_MESSAGE";
  public static final String ORDER_NUMBER = "ORDER_NUMBER";
  private static final String ORDER_TRACKING_PROMPT_VERSION = "v1";

  private final MultiModelProviderService multiModelProviderService;

  private final ToolsFunctionConfig toolsFunctionConfig;
  private final ResourceLoader resourceLoader;
  private final CacheManager cacheManager;


  public String getOrderInformationFromLLM(String orderNumber, String userMessage, String aiProvider){
    String cacheKey = buildOrderTrackingCacheKey(orderNumber, userMessage, aiProvider);
    Cache cache = cacheManager.getCache(CacheConfig.ORDER_LLM_RESPONSE_CACHE);
    if (cache != null) {
      String cached = cache.get(cacheKey, String.class);
      if (cached != null) {
        log.info("[{}] HIT key={} -> returning cached response (LLM/tools not invoked)",
          CacheConfig.ORDER_LLM_RESPONSE_CACHE, cacheKey);
        return cached;
      }
      log.info("[{}] MISS key={} -> invoking LLM/tools",
        CacheConfig.ORDER_LLM_RESPONSE_CACHE, cacheKey);
    }

    String normalizedOrderNumber = normalizeOrderNumber(orderNumber);
    String normalizedMessage = normalizeMessage(userMessage);
    String normalizedProvider = normalizeProvider(aiProvider);

    Map<String, Object> substitutionVariables = Map.of(
      USER_MESSAGE, normalizedMessage,
      ORDER_NUMBER, normalizedOrderNumber
    );
    String response = callModelWithTemplate(
      "classpath:prompts/user-prompts/order-tracking-user.txt",
      substitutionVariables,
      normalizedProvider,
      toolsFunctionConfig
    );

    if (cache != null && shouldCache(response)) {
      cache.put(cacheKey, response);
      log.info("[{}] STORE key={}", CacheConfig.ORDER_LLM_RESPONSE_CACHE, cacheKey);
    } else if (cache != null) {
      log.info("[{}] SKIP-STORE key={} -> fallback/error response", CacheConfig.ORDER_LLM_RESPONSE_CACHE, cacheKey);
    }
    return response;
  }

  private String callModelWithTemplate(
    String templatePath,
    Map<String, Object> substitutionVariables,
    String aiProvider,
    Object... toolCallbacks
  ) {
    String userPromptTemplate = PromptReaderUtil.getPrompt(resourceLoader, templatePath);
    PromptTemplate template = new PromptTemplate(userPromptTemplate);
    String llmResponse = multiModelProviderService.executeWithTimeoutOrFallback(
      "tools flow response generation",
      () -> multiModelProviderService.getChatClient(aiProvider)
        .prompt()
        .user(template.create(substitutionVariables).getContents())
        .tools(toolCallbacks)
        .call()
        .content(),
      "Something went wrong while processing your request. Please try again."
    );
    return ensureGreeting(llmResponse);
  }

  private String ensureGreeting(String response) {
    if (response == null || response.isBlank()) {
      return "Hello, I could not generate a response right now.";
    }
    return response;
  }

  public String buildOrderTrackingCacheKey(String orderNumber, String userMessage, String aiProvider) {
    String normalizedOrderNumber = normalizeOrderNumber(orderNumber);
    String normalizedMessage = normalizeMessage(userMessage);
    String normalizedProvider = normalizeProvider(aiProvider);
    return String.join("|", normalizedProvider, normalizedOrderNumber, ORDER_TRACKING_PROMPT_VERSION, normalizedMessage);
  }

  private String normalizeOrderNumber(String orderNumber) {
    if (!StringUtils.hasText(orderNumber)) {
      return "NONE";
    }
    return orderNumber.trim().replaceAll("[\\p{Punct}]+$", "").toUpperCase(Locale.ROOT);
  }

  private String normalizeMessage(String userMessage) {
    if (!StringUtils.hasText(userMessage)) {
      return "";
    }
    return userMessage.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  private String normalizeProvider(String aiProvider) {
    if (!StringUtils.hasText(aiProvider)) {
      return "";
    }
    return aiProvider.trim().toLowerCase(Locale.ROOT);
  }

  private boolean shouldCache(String response) {
    if (!StringUtils.hasText(response)) {
      return false;
    }
    String normalized = response.trim().toLowerCase(Locale.ROOT);
    return !normalized.contains("something went wrong")
      && !normalized.contains("could not generate");
  }

}
