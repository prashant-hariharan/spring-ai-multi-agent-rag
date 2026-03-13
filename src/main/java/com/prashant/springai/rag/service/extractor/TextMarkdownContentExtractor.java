package com.prashant.springai.rag.service.extractor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Component
@Slf4j
public class TextMarkdownContentExtractor implements DocumentContentExtractor {

  @Override
  public boolean supports(String filename) {
    String normalized = normalize(filename);
    return normalized.endsWith(".txt") || normalized.endsWith(".md");
  }

  @Override
  public String extractContent(MultipartFile file) {
    try {
      return new String(file.getBytes(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.error("Failed to parse text/markdown file: {}", file.getOriginalFilename(), e);
      throw new IllegalArgumentException("Failed to read text/markdown file", e);
    }
  }

  @Override
  public List<String> supportedExtensions() {
    return List.of(".txt", ".md");
  }

  private String normalize(String filename) {
    return filename == null ? "" : filename.toLowerCase(Locale.ROOT).trim();
  }
}
