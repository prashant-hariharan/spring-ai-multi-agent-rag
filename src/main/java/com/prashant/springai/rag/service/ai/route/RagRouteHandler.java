package com.prashant.springai.rag.service.ai.route;

import com.prashant.springai.rag.dto.AgentQueryResponse;
import com.prashant.springai.rag.model.AgentIntent;
import com.prashant.springai.rag.model.AgentRoute;
import com.prashant.springai.rag.service.ai.RAGQueryService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class RagRouteHandler implements AgentRouteHandler {

  private final RAGQueryService ragQueryService;

  @Override
  public AgentRoute route() {
    return AgentRoute.RAG;
  }

  @Override
  public AgentQueryResponse handle(String question, String orderNumber, AgentIntent intent, String aiProvider) {
    String answer = ragQueryService.askQuestionWithAgentIntent(question, aiProvider, intent);
    return new AgentQueryResponse(true, route().name(), answer, null);
  }
}
