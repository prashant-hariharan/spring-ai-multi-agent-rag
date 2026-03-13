package com.prashant.springai.rag.repository;

import com.prashant.springai.rag.model.RagDocumentCatalog;
import com.prashant.springai.rag.model.RagDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RagDocumentCatalogRepository extends JpaRepository<RagDocumentCatalog, UUID> {
  Optional<RagDocumentCatalog> findByFileNameAndDocumentType(String fileName, RagDocumentType documentType);

  List<RagDocumentCatalog> findAllByDocumentTypeIn(Collection<RagDocumentType> documentTypes);
}
