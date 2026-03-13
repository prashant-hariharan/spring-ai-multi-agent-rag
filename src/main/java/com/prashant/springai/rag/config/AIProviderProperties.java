package com.prashant.springai.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "spring.ai")
@Data
public class AIProviderProperties {
  private Map<String, Provider> providers;
  @Data
  public static class Provider {
    private String apiKey;
    private String model;
    private String baseUrl;
    private String completionPath;
    private Double temperature;
    private Integer maxTokens;
  }
}
