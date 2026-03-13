package com.prashant.springai.rag.service.ai.route;

import com.prashant.springai.rag.dto.AgentQueryResponse;
import com.prashant.springai.rag.model.AgentIntent;
import com.prashant.springai.rag.model.AgentRoute;
import com.prashant.springai.rag.service.ai.LLMOrderTrackingService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@AllArgsConstructor
public class ToolsRouteHandler implements AgentRouteHandler {

  private static final String ORDER_NUMBER_REQUIRED_MESSAGE =
    "Please include a valid order number (example: ORD-0001).";

  private final LLMOrderTrackingService orderTrackingService;

  @Override
  public AgentRoute route() {
    return AgentRoute.TOOLS;
  }

  @Override
  public AgentQueryResponse handle(String question, String orderNumber, AgentIntent intent, String aiProvider) {
    if (!StringUtils.hasText(orderNumber)) {
      return new AgentQueryResponse(false, route().name(), null, ORDER_NUMBER_REQUIRED_MESSAGE);
    }

    String answer = orderTrackingService.getOrderInformationFromLLM(orderNumber, question, aiProvider);
    return new AgentQueryResponse(true, route().name(), answer, null);
  }
}
