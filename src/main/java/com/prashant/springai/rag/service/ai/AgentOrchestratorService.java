package com.prashant.springai.rag.service.ai;

import com.prashant.springai.rag.dto.AgentQueryRequest;
import com.prashant.springai.rag.dto.AgentQueryResponse;
import com.prashant.springai.rag.model.AgentClassificationResult;
import com.prashant.springai.rag.model.AgentIntent;
import com.prashant.springai.rag.model.AgentRoute;
import com.prashant.springai.rag.service.ai.AnswerEvaluationService.AnswerEvaluationResult;
import com.prashant.springai.rag.service.ai.route.AgentRouteHandler;
import com.prashant.springai.rag.service.ai.route.RouteExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class AgentOrchestratorService {

  private static final Pattern ORDER_NUMBER_PATTERN = Pattern.compile("\\bORD-\\d{4}\\b", Pattern.CASE_INSENSITIVE);

  private final AgentClassificationService agentClassificationService;
  private final Map<AgentRoute, AgentRouteHandler> routeHandlerMap;
  private final AnswerEvaluationService answerEvaluationService;

  @Value("${app.ai.evaluation.max-attempts:2}")
  private int maxEvaluationAttempts;

  public AgentQueryResponse process(AgentQueryRequest request, String aiProvider) {
    String question = request.query();
    String orderNumber = extractOrderNumber(question);
    AgentClassificationResult classification = agentClassificationService.classify(question, orderNumber, aiProvider);
    AgentRoute route = classification.route();
    AgentIntent intent = classification.intent();

    log.info("Agent classification result route={}, intent={}, orderNumber={}", route, intent, orderNumber);

    AgentRouteHandler handler = routeHandlerMap.get(route);
    if (handler == null) {
      log.error("No handler configured for route={}", route);
      return new AgentQueryResponse(false, route.name(), null, "No handler configured for route " + route.name());
    }
    log.info("Dispatching to handler={} for route={}", handler.getClass().getSimpleName(), route);

    String retryInstruction = null;
    AgentQueryResponse lastResponse = null;
    int attempts = Math.max(1, maxEvaluationAttempts);

    for (int attempt = 1; attempt <= attempts; attempt++) {
      RouteExecutionResult executionResult = handler.handle(question, orderNumber, intent, aiProvider, retryInstruction);
      lastResponse = executionResult.response();
      if (!lastResponse.success()) {
        return lastResponse;
      }

      AnswerEvaluationResult evaluationResult = answerEvaluationService.evaluate(
        route,
        question,
        executionResult.evidence(),
        lastResponse.answer()
      );

      if (evaluationResult.pass()) {
        if (attempt > 1) {
          log.info("Answer passed evaluation after retry attempt={}", attempt);
        }
        return lastResponse;
      }

      retryInstruction = evaluationResult.feedback();
      log.warn("Answer failed evaluation on attempt={} route={} feedback={}", attempt, route, retryInstruction);
    }

    return new AgentQueryResponse(
      false,
      route.name(),
      lastResponse == null ? null : lastResponse.answer(),
      "Generated answer failed validation after " + attempts + " attempt(s)."
    );
  }

  private String extractOrderNumber(String question) {
    if (!StringUtils.hasText(question)) {
      return null;
    }
    Matcher matcher = ORDER_NUMBER_PATTERN.matcher(question);
    if (!matcher.find()) {
      return null;
    }
    return matcher.group().toUpperCase();
  }
}
