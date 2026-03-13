package com.prashant.springai.rag.service.extractor;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;

@Component
@Slf4j
public class PdfContentExtractor implements DocumentContentExtractor {

  @Override
  public boolean supports(String filename) {
    String normalized = normalize(filename);
    return normalized.endsWith(".pdf");
  }

  @Override
  public String extractContent(MultipartFile file) {
    try (InputStream inputStream = file.getInputStream();
         PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
      return new PDFTextStripper().getText(document);
    } catch (Exception e) {
      log.error("Failed to parse PDF file: {}", file.getOriginalFilename(), e);
      throw new IllegalArgumentException("Failed to read PDF file", e);
    }
  }

  @Override
  public List<String> supportedExtensions() {
    return List.of(".pdf");
  }

  private String normalize(String filename) {
    return filename == null ? "" : filename.toLowerCase(Locale.ROOT).trim();
  }
}
