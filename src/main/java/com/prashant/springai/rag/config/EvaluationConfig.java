package com.prashant.springai.rag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EvaluationConfig {

  @Bean
  public RelevancyEvaluator relevancyEvaluator(ChatClient.Builder chatClientBuilder) {
    return new RelevancyEvaluator(chatClientBuilder);
  }

  @Bean
  public FactCheckingEvaluator factCheckingEvaluator(ChatClient.Builder chatClientBuilder) {
    return FactCheckingEvaluator.builder(chatClientBuilder).build();
  }
}
