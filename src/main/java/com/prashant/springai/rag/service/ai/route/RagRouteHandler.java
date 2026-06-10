package com.prashant.springai.rag.service.ai.route;

import com.prashant.springai.rag.dto.AgentQueryResponse;
import com.prashant.springai.rag.model.AgentIntent;
import com.prashant.springai.rag.model.AgentRoute;
import com.prashant.springai.rag.service.ai.RAGQueryService;
import com.prashant.springai.rag.service.ai.RAGQueryService.RagAnswerResult;
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
  public RouteExecutionResult handle(
    String question,
    String orderNumber,
    AgentIntent intent,
    String aiProvider,
    String retryInstruction
  ) {
    RagAnswerResult result = ragQueryService.askQuestionWithAgentIntentAndEvidence(
      question,
      aiProvider,
      intent,
      retryInstruction
    );
    return new RouteExecutionResult(
      new AgentQueryResponse(true, route().name(), result.answer(), null),
      result.evidence()
    );
  }
}
