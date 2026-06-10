package com.prashant.springai.rag.service.ai;

import com.prashant.springai.rag.model.AgentRoute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AnswerEvaluationService {

  private final RelevancyEvaluator relevancyEvaluator;
  private final FactCheckingEvaluator factCheckingEvaluator;

  @Value("${app.ai.evaluation.enabled:true}")
  private boolean evaluationEnabled;

  public AnswerEvaluationResult evaluate(AgentRoute route, String question, String evidence, String answer) {
    if (!evaluationEnabled) {
      return AnswerEvaluationResult.passed();
    }
    if (!StringUtils.hasText(answer)) {
      return AnswerEvaluationResult.failed("Answer is empty.");
    }
    log.info("Evaluating and fact-checking agent for question {}", question);

    try {
      EvaluationResponse relevancy = evaluateRelevancy(question, evidence, answer);

      if (!relevancy.isPass()) {
        return AnswerEvaluationResult.failed("Answer failed relevancy evaluation: " + relevancy.getFeedback());
      }

      if (requiresFactChecking(route) && StringUtils.hasText(evidence)) {
        EvaluationResponse factuality = factCheckingEvaluator.evaluate(
          new EvaluationRequest(question, List.of(new Document(evidence)), answer)
        );
        if (!factuality.isPass()) {
          return AnswerEvaluationResult.failed("Answer failed factuality evaluation: " + factuality.getFeedback());
        }
      }
      log.info("Evaluation is relevant and fact checked for question {}", question);
      return AnswerEvaluationResult.passed();
    } catch (RuntimeException ex) {
      log.warn("Answer evaluation failed. Allowing response to avoid blocking the user flow.", ex);
      return AnswerEvaluationResult.passed();
    }
  }

  private EvaluationResponse evaluateRelevancy(String question, String evidence, String answer) {
    if (StringUtils.hasText(evidence)) {
      return relevancyEvaluator.evaluate(new EvaluationRequest(question, List.of(new Document(evidence)), answer));
    }
    return relevancyEvaluator.evaluate(new EvaluationRequest(question, answer));
  }

  private boolean requiresFactChecking(AgentRoute route) {
    return route == AgentRoute.RAG || route == AgentRoute.TOOLS || route == AgentRoute.COMBINED;
  }

  public record AnswerEvaluationResult(
    boolean pass,
    String feedback
  ) {
    static AnswerEvaluationResult passed() {
      return new AnswerEvaluationResult(true, null);
    }

    static AnswerEvaluationResult failed(String feedback) {
      return new AnswerEvaluationResult(false, feedback);
    }
  }
}
