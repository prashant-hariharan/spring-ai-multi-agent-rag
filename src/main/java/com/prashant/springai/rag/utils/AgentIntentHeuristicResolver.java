package com.prashant.springai.rag.utils;

import com.prashant.springai.rag.model.AgentIntent;
import com.prashant.springai.rag.model.AgentRoute;

import java.util.Set;

public final class AgentIntentHeuristicResolver {

  private static final Set<String> POLICY_KEYWORDS = Set.of(
    "policy", "return", "refund", "shipping", "warranty", "terms"
  );
  private static final Set<String> EMPLOYEE_KEYWORDS = Set.of(
    "employee", "handbook", "hr", "leave", "benefits", "payroll", "internal"
  );

  private AgentIntentHeuristicResolver() {
  }

  public static AgentIntent resolve(String question, AgentRoute route) {
    if (route == AgentRoute.TOOLS || route == AgentRoute.GENERAL) {
      return AgentIntent.NONE;
    }

    String normalizedQuestion = question == null ? "" : question.toLowerCase();
    boolean policyIntent = containsAny(normalizedQuestion, POLICY_KEYWORDS);
    boolean employeeIntent = containsAny(normalizedQuestion, EMPLOYEE_KEYWORDS);

    if (policyIntent && employeeIntent) {
      return AgentIntent.POLICY_AND_EMPLOYEE;
    }
    if (employeeIntent) {
      return AgentIntent.EMPLOYEE_HANDBOOK;
    }
    return AgentIntent.POLICY;
  }

  private static boolean containsAny(String text, Set<String> keywords) {
    for (String keyword : keywords) {
      if (text.contains(keyword)) {
        return true;
      }
    }
    return false;
  }
}
