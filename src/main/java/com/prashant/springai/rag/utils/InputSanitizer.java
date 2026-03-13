package com.prashant.springai.rag.utils;

public final class InputSanitizer {

  private InputSanitizer() {
  }

  /**
   * Sanitizes user input for LLM processing.
   * - Trims leading/trailing whitespace
   * - Normalizes line endings
   * - Removes non-printable control characters (except \n, \r, \t)
   */
  public static String sanitize(String input) {

    if (input == null) {
      return null;
    }

    // Trim leading/trailing whitespace
    String sanitized = input.trim();

    // Normalize Windows line endings to Unix
    sanitized = sanitized.replace("\r\n", "\n");

    // Remove control characters except newline, carriage return, tab
    sanitized = sanitized.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");

    return sanitized;
  }
}

