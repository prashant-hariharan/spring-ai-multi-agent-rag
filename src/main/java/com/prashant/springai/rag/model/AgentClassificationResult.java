package com.prashant.springai.rag.model;

public record AgentClassificationResult(
  AgentRoute route,
  AgentIntent intent
) {
}
