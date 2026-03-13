package com.prashant.springai.rag.dto;

public record AgentQueryResponse(
  boolean success,
  String route,
  String answer,
  String error
) {
}
