package com.prashant.springai.rag.service.extractor;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;

@Component
@Slf4j
public class WordContentExtractor implements DocumentContentExtractor {

  @Override
  public boolean supports(String filename) {
    String normalized = normalize(filename);
    return normalized.endsWith(".docx") || normalized.endsWith(".doc");
  }

  @Override
  public String extractContent(MultipartFile file) {
    String filename = normalize(file.getOriginalFilename());
    try {
      if (filename.endsWith(".docx")) {
        return extractDocx(file);
      }
      if (filename.endsWith(".doc")) {
        return extractDoc(file);
      }
      throw new IllegalArgumentException("Unsupported Word file extension");
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to parse Word file: {}", file.getOriginalFilename(), e);
      throw new IllegalArgumentException("Failed to read Word file", e);
    }
  }

  @Override
  public List<String> supportedExtensions() {
    return List.of(".doc", ".docx");
  }

  private String extractDocx(MultipartFile file) throws Exception {
    try (InputStream inputStream = file.getInputStream();
         XWPFDocument document = new XWPFDocument(inputStream);
         XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
      return extractor.getText();
    }
  }

  private String extractDoc(MultipartFile file) throws Exception {
    try (InputStream inputStream = file.getInputStream();
         HWPFDocument document = new HWPFDocument(inputStream);
         WordExtractor extractor = new WordExtractor(document)) {
      return extractor.getText();
    }
  }

  private String normalize(String filename) {
    return filename == null ? "" : filename.toLowerCase(Locale.ROOT).trim();
  }
}
