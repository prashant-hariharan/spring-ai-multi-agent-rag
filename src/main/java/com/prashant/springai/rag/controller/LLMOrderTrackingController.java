package com.prashant.springai.rag.controller;

import com.prashant.springai.rag.service.ai.LLMOrderTrackingService;
import com.prashant.springai.rag.utils.AIProviderConstants;


import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/order-tracking")
@AllArgsConstructor
public class LLMOrderTrackingController {
  private final LLMOrderTrackingService orderTrackingService;

  @GetMapping
  public String getOrderTracking(
      @RequestParam String orderNumber,
      @RequestParam String userMessage,
      @RequestHeader(value = AIProviderConstants.AI_PROVIDER_HEADER, required = false, defaultValue = AIProviderConstants.OLLAMA) String aiProvider) {
    return orderTrackingService.getOrderInformationFromLLM(orderNumber, userMessage, aiProvider);
  }
}
