package com.prashant.springai.rag.service.ai.route;

import com.prashant.springai.rag.dto.AgentQueryResponse;
import com.prashant.springai.rag.model.AgentIntent;
import com.prashant.springai.rag.model.AgentRoute;
import com.prashant.springai.rag.service.ai.MultiModelProviderService;
import com.prashant.springai.rag.utils.AIProviderConstants;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class GeneralRouteHandler implements AgentRouteHandler {

  private final MultiModelProviderService multiModelProviderService;

  @Override
  public AgentRoute route() {
    return AgentRoute.GENERAL;
  }

  @Override
  public AgentQueryResponse handle(String question, String orderNumber, AgentIntent intent, String aiProvider) {
    if (!multiModelProviderService.getChatClients().containsKey(AIProviderConstants.OLLAMA)) {
      return new AgentQueryResponse(
        true,
        route().name(),
        "No cheaper model is configured right now. Please configure 'ollama' or try another provider.",
        null
      );
    }

    String answer = multiModelProviderService.executeWithTimeoutOrFallback(
      "general route response generation",
      () -> multiModelProviderService.getChatClient(AIProviderConstants.OLLAMA)
        .prompt()
        .user(question)
        .call()
        .content(),
      "Something went wrong while generating the response. Please try again."
    );

    return new AgentQueryResponse(true, route().name(), answer, null);
  }
}
