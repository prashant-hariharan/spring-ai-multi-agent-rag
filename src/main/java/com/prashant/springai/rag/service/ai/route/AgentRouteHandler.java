package com.prashant.springai.rag.service.ai.route;

import com.prashant.springai.rag.model.AgentIntent;
import com.prashant.springai.rag.model.AgentRoute;

public interface AgentRouteHandler {
  AgentRoute route();

  RouteExecutionResult handle(
    String question,
    String orderNumber,
    AgentIntent intent,
    String aiProvider,
    String retryInstruction
  );
}
