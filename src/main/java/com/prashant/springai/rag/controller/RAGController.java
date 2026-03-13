package com.prashant.springai.rag.controller;

import com.prashant.springai.rag.model.RagDocumentType;
import com.prashant.springai.rag.service.ai.RAGIngesterService;
import com.prashant.springai.rag.service.ai.RAGQueryService;
import com.prashant.springai.rag.service.extractor.DocumentContentExtractionService;
import com.prashant.springai.rag.utils.AIProviderConstants;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rag")
@Slf4j
@AllArgsConstructor
public class RAGController {

  private final RAGIngesterService documentService;
  private final RAGQueryService ragQueryService;
  private final DocumentContentExtractionService contentExtractionService;

  @PostMapping("/upload")
  public ResponseEntity<Map<String, Object>> uploadDocument(
    @RequestParam("file") MultipartFile file,
    @RequestParam("type") RagDocumentType documentType
  ) {

    log.info("Uploading file: {}", file.getOriginalFilename());

    String filename = file.getOriginalFilename();
    String content;
    try {
      content = contentExtractionService.extractContent(file);
    } catch (IllegalArgumentException e) {
      Map<String, Object> response = new HashMap<>();
      response.put("success", false);
      response.put("error", e.getMessage());
      response.put("supportedTypes", contentExtractionService.supportedTypesDescription());
      return ResponseEntity.badRequest().body(response);
    }

    int chunks = documentService.loadAndIndexDocumentFromString(content, filename, documentType);

    Map<String, Object> response = new HashMap<>();
    response.put("success", true);
    response.put("message", "Document indexed successfully");
    response.put("filename", filename);
    response.put("type", documentType.name());
    response.put("chunks", chunks);

    return ResponseEntity.ok(response);
  }

  @PostMapping("/query")
  public ResponseEntity<Map<String, Object>> askQuestion(
    @RequestHeader(value = AIProviderConstants.AI_PROVIDER_HEADER, required = false,
      defaultValue = AIProviderConstants.OLLAMA) String aiProvider,
    @RequestParam(value = "fileName", required = false) String fileName,
    @RequestBody Map<String, String> request) {

    String question = request.get("question");
    if (!StringUtils.hasText(question)) {
      Map<String, Object> response = new HashMap<>();
      response.put("success", false);
      response.put("error", "Question is required");
      return ResponseEntity.badRequest().body(response);
    }

    List<String> fileNames = StringUtils.hasText(fileName) ? List.of(fileName) : List.of();
    String answer = ragQueryService.askQuestion(question, aiProvider, fileNames);

    Map<String, Object> response = new HashMap<>();
    response.put("success", true);
    response.put("answer", answer);
    if (StringUtils.hasText(fileName)) {
      response.put("fileName", fileName);
    }

    return ResponseEntity.ok(response);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    if ("type".equals(ex.getName()) && RagDocumentType.class.equals(ex.getRequiredType())) {
      Map<String, Object> response = new HashMap<>();
      response.put("success", false);
      response.put("error", "Invalid type. Use one of: POLICY, EMPLOYEE, GENERAL.");
      response.put("allowedTypes", List.of(
        RagDocumentType.POLICY.name(),
        RagDocumentType.EMPLOYEE.name(),
        RagDocumentType.GENERAL.name()
      ));
      return ResponseEntity.badRequest().body(response);
    }
    throw ex;
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<Map<String, Object>> handleMissingType(MissingServletRequestParameterException ex) {
    if ("type".equals(ex.getParameterName())) {
      Map<String, Object> response = new HashMap<>();
      response.put("success", false);
      response.put("error", "type is required.");
      response.put("allowedTypes", List.of(
        RagDocumentType.POLICY.name(),
        RagDocumentType.EMPLOYEE.name(),
        RagDocumentType.GENERAL.name()
      ));
      return ResponseEntity.badRequest().body(response);
    }
    Map<String, Object> response = new HashMap<>();
    response.put("success", false);
    response.put("error", ex.getMessage());
    return ResponseEntity.badRequest().body(response);
  }

}
