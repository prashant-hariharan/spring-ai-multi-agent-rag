package com.prashant.springai.rag.service.ai.route;

import com.prashant.springai.rag.dto.AgentQueryResponse;
import com.prashant.springai.rag.dto.OrderDTO;
import com.prashant.springai.rag.exception.InvalidInputException;
import com.prashant.springai.rag.exception.ResourceNotFoundException;
import com.prashant.springai.rag.model.AgentIntent;
import com.prashant.springai.rag.model.AgentRoute;
import com.prashant.springai.rag.service.OrderService;
import com.prashant.springai.rag.service.ai.MultiModelProviderService;
import com.prashant.springai.rag.service.ai.RAGQueryService;
import com.prashant.springai.rag.utils.PromptReaderUtil;
import lombok.AllArgsConstructor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Component
@AllArgsConstructor
public class CombinedRouteHandler implements AgentRouteHandler {

  private static final String ORDER_NUMBER_REQUIRED_MESSAGE =
    "Please include a valid order number (example: ORD-0001).";
  private static final String COMBINED_SYNTHESIS_PROMPT_PATH =
    "classpath:prompts/user-prompts/combined-route-synthesis-user.txt";

  private final RAGQueryService ragQueryService;
  private final OrderService orderService;
  private final MultiModelProviderService multiModelProviderService;
  private final ResourceLoader resourceLoader;

  @Override
  public AgentRoute route() {
    return AgentRoute.COMBINED;
  }

  @Override
  public RouteExecutionResult handle(
    String question,
    String orderNumber,
    AgentIntent intent,
    String aiProvider,
    String retryInstruction
  ) {
    if (!StringUtils.hasText(orderNumber)) {
      return new RouteExecutionResult(
        new AgentQueryResponse(false, route().name(), null, ORDER_NUMBER_REQUIRED_MESSAGE),
        ""
      );
    }

    OrderDTO order;
    try {
      order = orderService.getOrderByNumber(orderNumber);
    } catch (InvalidInputException | ResourceNotFoundException ex) {
      return new RouteExecutionResult(
        new AgentQueryResponse(false, route().name(), null, ex.getMessage()),
        ""
      );
    }

    String policyContext = ragQueryService.fetchRelevantContextWithAgentIntent(question, intent);
    if (!StringUtils.hasText(policyContext)) {
      policyContext = "No matching policy context found in the knowledge base.";
    }

    String promptTemplate = PromptReaderUtil.getPrompt(resourceLoader, COMBINED_SYNTHESIS_PROMPT_PATH);
    PromptTemplate template = new PromptTemplate(promptTemplate);
    Map<String, Object> substitutionVariables = Map.of(
      "QUESTION", question,
      "ORDER_FACTS", formatOrderFacts(order),
      "POLICY_CONTEXT", policyContext
    );

    String userPrompt = template.create(substitutionVariables).getContents();
    if (StringUtils.hasText(retryInstruction)) {
      userPrompt = userPrompt + "\n\nValidation feedback for retry:\n" + retryInstruction
        + "\nRevise the answer so every claim is supported by the order facts or policy context.";
    }
    String finalUserPrompt = userPrompt;

    String combinedAnswer = multiModelProviderService.executeWithTimeoutOrFallback(
      "combined route synthesis",
      () -> multiModelProviderService.getChatClient(aiProvider)
        .prompt()
        .user(finalUserPrompt)
        .call()
        .content(),
      "Something went wrong while combining policy and order details. Please try again."
    );

    return new RouteExecutionResult(
      new AgentQueryResponse(true, route().name(), combinedAnswer, null),
      "Order facts:\n" + formatOrderFacts(order) + "\n\nPolicy context:\n" + policyContext
    );
  }

  private String formatOrderFacts(OrderDTO order) {
    return """
      orderNumber: %s
      customerName: %s
      productName: %s
      quantity: %d
      unitPrice: %s
      status: %s
      createdAt: %s
      deliveryInfo: %s
      """.formatted(
      order.orderNumber(),
      order.customerName(),
      order.productName(),
      order.quantity(),
      order.unitPrice(),
      order.status(),
      order.createdAt(),
      order.deliveryInfo() == null ? "" : order.deliveryInfo()
    ).trim();
  }
}
