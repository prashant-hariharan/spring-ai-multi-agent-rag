package com.prashant.springai.rag.service.ai.route;

import com.prashant.springai.rag.dto.AgentQueryResponse;
import com.prashant.springai.rag.dto.OrderDTO;
import com.prashant.springai.rag.model.AgentIntent;
import com.prashant.springai.rag.model.AgentRoute;
import com.prashant.springai.rag.service.OrderService;
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
  private final OrderService orderService;

  @Override
  public AgentRoute route() {
    return AgentRoute.TOOLS;
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

    String effectiveQuestion = withRetryInstruction(question, retryInstruction);
    String answer = orderTrackingService.getOrderInformationFromLLM(orderNumber, effectiveQuestion, aiProvider);
    return new RouteExecutionResult(
      new AgentQueryResponse(true, route().name(), answer, null),
      formatOrderFacts(orderService.getOrderByNumber(orderNumber))
    );
  }

  private String withRetryInstruction(String question, String retryInstruction) {
    if (!StringUtils.hasText(retryInstruction)) {
      return question;
    }
    return question + "\n\nValidation feedback for retry:\n" + retryInstruction
      + "\nRevise the answer so every order-related claim matches the order facts.";
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
