package com.prashant.springai.rag.service.extractor;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentContentExtractionService {

  private final List<DocumentContentExtractor> extractors;

  public String extractContent(MultipartFile file) {
    String filename = file.getOriginalFilename();

    return extractors.stream()
      .filter(extractor -> extractor.supports(filename))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Unsupported file type: " + filename))
      .extractContent(file);
  }

  public String supportedTypesDescription() {
    return extractors.stream()
      .flatMap(extractor -> extractor.supportedExtensions().stream())
      .distinct()
      .collect(Collectors.joining(", "));
  }
}
