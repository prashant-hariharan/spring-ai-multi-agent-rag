package com.prashant.springai.rag.service.ai;

import com.prashant.springai.rag.utils.AIProviderConstants;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.Map;
import java.util.Optional;

@Service
@Getter
@Slf4j
@RequiredArgsConstructor
public class MultiModelProviderService {

  private final Map<String, ChatClient> chatClients;

  @Value("${app.ai.llm-timeout-seconds:30}")
  private long llmTimeoutSeconds;

  public ChatClient getChatClient(String aiProvider) {
    String provider = StringUtils.hasText(aiProvider) ? aiProvider : AIProviderConstants.OLLAMA;

    ChatClient client = Optional.ofNullable(chatClients.get(provider))
      .or(() -> Optional.ofNullable(chatClients.get(AIProviderConstants.OLLAMA)))
      .or(() -> Optional.ofNullable(chatClients.get(AIProviderConstants.OPENAI)))
      .or(() -> chatClients.values().stream().findFirst())
      .orElseThrow(() -> new IllegalStateException(
        "No ChatClient beans are configured. Add at least one provider under spring.ai.openai or spring.ai.providers.*."
      ));

    log.info("Chat client selected for provider: {}", provider);
    return client;
  }

  public <T> T executeWithTimeoutOrThrow(String operationName, Supplier<T> supplier) {
    try {
      return CompletableFuture.supplyAsync(supplier)
        .orTimeout(llmTimeoutSeconds, TimeUnit.SECONDS)
        .join();
    } catch (CompletionException ex) {
      Throwable cause = ex.getCause();
      if (cause instanceof TimeoutException) {
        String message = "LLM request timed out after " + llmTimeoutSeconds + " seconds during " + operationName + ".";
        log.error(message);
        throw new RuntimeException(message, cause);
      }
      throw ex;
    }
  }

  public String executeWithTimeoutOrFallback(String operationName, Supplier<String> supplier, String fallbackMessage) {
    try {
      return executeWithTimeoutOrThrow(operationName, supplier);
    } catch (RuntimeException ex) {
      log.warn("LLM call failed during {}. Returning fallback response.", operationName, ex);
      return fallbackMessage;
    }
  }
}
