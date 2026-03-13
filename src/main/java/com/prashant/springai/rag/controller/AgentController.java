package com.prashant.springai.rag.controller;

import com.prashant.springai.rag.dto.AgentQueryRequest;
import com.prashant.springai.rag.dto.AgentQueryResponse;
import com.prashant.springai.rag.service.ai.AgentOrchestratorService;
import com.prashant.springai.rag.utils.AIProviderConstants;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent")
@AllArgsConstructor
public class AgentController {

  private final AgentOrchestratorService agentOrchestratorService;

  @PostMapping("/query")
  public ResponseEntity<AgentQueryResponse> query(
    @RequestHeader(
      value = AIProviderConstants.AI_PROVIDER_HEADER,
      required = false,
      defaultValue = AIProviderConstants.OLLAMA
    ) String aiProvider,
    @RequestBody AgentQueryRequest request
  ) {
    if (request == null || !StringUtils.hasText(request.query())) {
      return ResponseEntity.badRequest()
        .body(new AgentQueryResponse(false, null, null, "query is required"));
    }

    AgentQueryResponse response = agentOrchestratorService.process(request, aiProvider);
    if (!response.success()) {
      return ResponseEntity.badRequest().body(response);
    }
    return ResponseEntity.ok(response);
  }
}
