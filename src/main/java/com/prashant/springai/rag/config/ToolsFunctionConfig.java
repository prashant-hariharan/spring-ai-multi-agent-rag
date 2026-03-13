package com.prashant.springai.rag.config;

import com.prashant.springai.rag.dto.OrderDTO;
import com.prashant.springai.rag.dto.OrderLookupResult;
import com.prashant.springai.rag.exception.InvalidInputException;
import com.prashant.springai.rag.exception.ResourceNotFoundException;
import com.prashant.springai.rag.service.OrderService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@AllArgsConstructor
public class ToolsFunctionConfig {
  private final OrderService orderService;

  @Tool(name = "getOrderByNumber", description = "Returns the order with the given order number.")
  public OrderLookupResult getOrderByNumber(@ToolParam(description = "The order number used to fetch order details") String orderNumber) {
    try {
      OrderDTO order = orderService.getOrderByNumber(orderNumber);
      log.info("Tool invoked: getOrderByNumber(orderNumber={}) -> found=true", orderNumber);
      return successResult(orderNumber, order, "Order found");
    } catch (InvalidInputException ex) {
      log.info("Tool invoked: getOrderByNumber(orderNumber={}) -> validationFailure={}", orderNumber, ex.getMessage());
      return validationFailureResult(orderNumber, ex);
    } catch (ResourceNotFoundException ex) {
      log.info("Tool invoked: getOrderByNumber(orderNumber={}) -> found=false", orderNumber);
      return notFoundResult(orderNumber, ex);
    }
  }

  private OrderLookupResult successResult(String orderNumber, OrderDTO order, String message) {
    return new OrderLookupResult(true, true, orderNumber, order, message, null);
  }

  private OrderLookupResult notFoundResult(String orderNumber, ResourceNotFoundException ex) {
    return new OrderLookupResult(false, false, orderNumber, null, ex.getMessage(), "ORDER_NOT_FOUND");
  }

  private OrderLookupResult validationFailureResult(String orderNumber, InvalidInputException ex) {
    return new OrderLookupResult(false, false, orderNumber, null, ex.getMessage(), "ORDER_NUMBER_INVALID_FORMAT");
  }

}
