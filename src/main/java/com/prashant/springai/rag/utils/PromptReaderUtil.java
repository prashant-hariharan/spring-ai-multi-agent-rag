package com.prashant.springai.rag.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
public final class PromptReaderUtil {

  private PromptReaderUtil() {}


 public static String getPrompt(ResourceLoader resourceLoader,String path) {
   try {
     Resource resource = resourceLoader.getResource(path);
     return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8).trim();
   } catch (IOException ex) {
     log.error("Failed to load {}", path, ex);
     throw new IllegalStateException("Failed to load "+ path, ex);

   }
 }
}
