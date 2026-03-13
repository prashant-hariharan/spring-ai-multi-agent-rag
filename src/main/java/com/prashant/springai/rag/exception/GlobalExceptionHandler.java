package com.prashant.springai.rag.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(NonTransientAiException.class)
  public ResponseEntity<Map<String, Object>> handleNonTransientAi(
    NonTransientAiException ex,
    HttpServletRequest request
  ) {
    if (isGuardrailBlockedError(ex)) {
      log.warn("Guardrail blocked request for path {}", request.getRequestURI(), ex);
      return buildErrorResponse(
        HttpStatus.FORBIDDEN,
        "GUARDRAIL_BLOCKED",
        "Your request was blocked by safety guardrails. Please rephrase and avoid sensitive personal data.",
        request
      );
    }

    log.error("Non-transient AI exception for path {}", request.getRequestURI(), ex);
    return buildErrorResponse(
      HttpStatus.BAD_GATEWAY,
      "AI_UPSTREAM_ERROR",
      "AI provider request failed. Please retry in a moment.",
      request
    );
  }


  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleUnhandled(Exception ex, HttpServletRequest request) {
    log.error("Unhandled exception for path {}", request.getRequestURI(), ex);
    return buildErrorResponse(
      HttpStatus.INTERNAL_SERVER_ERROR,
      "INTERNAL_SERVER_ERROR",
      "Something went wrong while processing your request. Please try again.",
      request
    );
  }

  private ResponseEntity<Map<String, Object>> buildErrorResponse(
    HttpStatus status,
    String code,
    String message,
    HttpServletRequest request
  ) {
    Map<String, Object> payload = Map.of(
      "timestamp", Instant.now().toString(),
      "status", status.value(),
      "error", status.getReasonPhrase(),
      "code", code,
      "message", message,
      "path", request.getRequestURI()
    );
    return ResponseEntity.status(status).body(payload);
  }

  private boolean isGuardrailBlockedError(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (containsGuardrailBlockMarkers(current.getMessage())) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private boolean containsGuardrailBlockMarkers(String message) {
    if (!StringUtils.hasText(message)) {
      return false;
    }
    String normalized = message.toLowerCase(Locale.ROOT);
    return normalized.contains("content blocked")
      || normalized.contains("pattern detected")
      || normalized.contains("\"code\":\"403\"");
  }
}
