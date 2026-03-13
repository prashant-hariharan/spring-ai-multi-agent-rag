package com.prashant.springai.rag.dto;

public record OrderLookupResult(
  boolean found,
  boolean success,
  String orderNumber,
  OrderDTO order,
  String message,
  String errorCode
) {
}
