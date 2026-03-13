package com.prashant.springai.rag.utils;

import com.prashant.springai.rag.model.AgentRoute;
import org.springframework.util.StringUtils;

import java.util.Set;

public final class AgentRouteHeuristicResolver {

  private static final Set<String> POLICY_KEYWORDS = Set.of(
    "policy", "return policy", "refund policy", "shipping policy", "cancellation policy", "warranty", "terms"
  );
  private static final Set<String> ORDER_KEYWORDS = Set.of(
    "order", "track", "tracking", "status", "cancel", "delivery", "ship", "shipped", "delivered"
  );

  private AgentRouteHeuristicResolver() {
  }

  public static AgentRoute resolve(String question, String orderNumber) {
    String normalizedQuestion = question == null ? "" : question.toLowerCase();
    boolean policyIntent = containsAnyKeyword(normalizedQuestion, POLICY_KEYWORDS);
    boolean transactionalIntent = StringUtils.hasText(orderNumber) || containsAnyKeyword(normalizedQuestion, ORDER_KEYWORDS);

    if (policyIntent && transactionalIntent) {
      return AgentRoute.COMBINED;
    }
    if (policyIntent) {
      return AgentRoute.RAG;
    }
    if (transactionalIntent) {
      return AgentRoute.TOOLS;
    }
    return AgentRoute.GENERAL;
  }

  private static boolean containsAnyKeyword(String text, Set<String> keywords) {
    for (String keyword : keywords) {
      if (text.contains(keyword)) {
        return true;
      }
    }
    return false;
  }
}
