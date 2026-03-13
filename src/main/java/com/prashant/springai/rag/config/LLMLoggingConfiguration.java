package com.prashant.springai.rag.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class LLMLoggingConfiguration {

  private final LoggingSystem loggingSystem;

  @Value("${app.ai.llm-logging.enabled:false}")
  private boolean llmLoggingEnabled;

  public LLMLoggingConfiguration(LoggingSystem loggingSystem) {
    this.loggingSystem = loggingSystem;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void configureLlmAdvisorLogging() {
    LogLevel level = llmLoggingEnabled ? LogLevel.DEBUG : LogLevel.INFO;
    loggingSystem.setLogLevel("org.springframework.ai.chat.client.advisor", level);
    loggingSystem.setLogLevel(
      "org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor",
      level
    );
    loggingSystem.setLogLevel("org.springframework.ai.tool", level);
    loggingSystem.setLogLevel("org.springframework.ai.model.tool", level);
  }
}
