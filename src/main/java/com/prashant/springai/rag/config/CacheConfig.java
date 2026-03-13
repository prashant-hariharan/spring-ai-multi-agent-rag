package com.prashant.springai.rag.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

  public static final String INTENT_CLASSIFICATION_CACHE = "intentClassificationCache";
  public static final String ORDER_READ_CACHE = "orderReadCache";
  public static final String ORDER_LLM_RESPONSE_CACHE = "orderLlmResponseCache";
  public static final String RAG_QUERY_RESPONSE_CACHE = "ragQueryResponseCache";
  public static final String RAG_CONTEXT_CACHE = "ragContextCache";

  @Bean
  public CacheManager cacheManager(
    @Value("${app.cache.intent.ttl-minutes:30}") long intentTtlMinutes,
    @Value("${app.cache.intent.max-size:5000}") long intentCacheMaxSize,
    @Value("${app.cache.order-read.ttl-seconds:60}") long orderReadTtlSeconds,
    @Value("${app.cache.order-read.max-size:10000}") long orderReadCacheMaxSize,
    @Value("${app.cache.order-llm.ttl-seconds:45}") long orderLlmTtlSeconds,
    @Value("${app.cache.order-llm.max-size:5000}") long orderLlmCacheMaxSize,
    @Value("${app.cache.rag-answer.ttl-seconds:120}") long ragAnswerTtlSeconds,
    @Value("${app.cache.rag-answer.max-size:5000}") long ragAnswerCacheMaxSize,
    @Value("${app.cache.rag-context.ttl-seconds:120}") long ragContextTtlSeconds,
    @Value("${app.cache.rag-context.max-size:5000}") long ragContextCacheMaxSize
  ) {
    CaffeineCacheManager cacheManager = new CaffeineCacheManager(
      INTENT_CLASSIFICATION_CACHE,
      ORDER_READ_CACHE,
      ORDER_LLM_RESPONSE_CACHE,
      RAG_QUERY_RESPONSE_CACHE,
      RAG_CONTEXT_CACHE
    );
    cacheManager.setCaffeine(
      Caffeine.newBuilder()
        .expireAfterWrite(intentTtlMinutes, TimeUnit.MINUTES)
        .maximumSize(intentCacheMaxSize)
    );
    cacheManager.registerCustomCache(
      ORDER_READ_CACHE,
      Caffeine.newBuilder()
        .expireAfterWrite(orderReadTtlSeconds, TimeUnit.SECONDS)
        .maximumSize(orderReadCacheMaxSize)
        .build()
    );
    cacheManager.registerCustomCache(
      ORDER_LLM_RESPONSE_CACHE,
      Caffeine.newBuilder()
        .expireAfterWrite(orderLlmTtlSeconds, TimeUnit.SECONDS)
        .maximumSize(orderLlmCacheMaxSize)
        .build()
    );
    cacheManager.registerCustomCache(
      RAG_QUERY_RESPONSE_CACHE,
      Caffeine.newBuilder()
        .expireAfterWrite(ragAnswerTtlSeconds, TimeUnit.SECONDS)
        .maximumSize(ragAnswerCacheMaxSize)
        .build()
    );
    cacheManager.registerCustomCache(
      RAG_CONTEXT_CACHE,
      Caffeine.newBuilder()
        .expireAfterWrite(ragContextTtlSeconds, TimeUnit.SECONDS)
        .maximumSize(ragContextCacheMaxSize)
        .build()
    );
    return cacheManager;
  }
}
