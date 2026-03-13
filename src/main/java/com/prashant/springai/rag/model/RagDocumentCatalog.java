package com.prashant.springai.rag.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
  name = "rag_document_catalog",
  uniqueConstraints = {
    @UniqueConstraint(name = "uk_rag_doc_catalog_file_type", columnNames = {"file_name", "document_type"})
  }
)
@Getter
@Setter
@NoArgsConstructor
public class RagDocumentCatalog {

  @Id
  @Column(name = "document_id", nullable = false, updatable = false)
  private UUID documentId;

  @Column(name = "file_name", nullable = false, length = 255)
  private String fileName;

  @Enumerated(EnumType.STRING)
  @Column(name = "document_type", nullable = false, length = 50)
  private RagDocumentType documentType;

  @Column(name = "source_system", nullable = false, length = 100)
  private String sourceSystem;

  @Column(name = "indexed_at", nullable = false)
  private Instant indexedAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  public void prePersist() {
    Instant now = Instant.now();
    if (this.documentId == null) {
      this.documentId = UUID.randomUUID();
    }
    if (this.indexedAt == null) {
      this.indexedAt = now;
    }
    if (this.updatedAt == null) {
      this.updatedAt = now;
    }
  }

  @PreUpdate
  public void preUpdate() {
    this.updatedAt = Instant.now();
  }
}
