package com.prashant.springai.rag.service.ai.route;

import com.prashant.springai.rag.dto.AgentQueryResponse;

public record RouteExecutionResult(
  AgentQueryResponse response,
  String evidence
) {
}
