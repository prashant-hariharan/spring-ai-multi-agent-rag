package com.prashant.springai.rag.dto;

import com.prashant.springai.rag.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderDTO(
  UUID orderId,
  String orderNumber,
  String customerName,
  String productName,
  int quantity,
  BigDecimal unitPrice,
  Instant createdAt,
  OrderStatus status,
  String deliveryInfo
) {
}
