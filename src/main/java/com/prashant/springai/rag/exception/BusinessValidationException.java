package com.prashant.springai.rag.exception;

public class BusinessValidationException extends RuntimeException {
  public BusinessValidationException(String message) {
    super(message);
  }
}
