package com.prashant.springai.rag.service.extractor;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentContentExtractor {
  boolean supports(String filename);

  String extractContent(MultipartFile file);

  List<String> supportedExtensions();
}
