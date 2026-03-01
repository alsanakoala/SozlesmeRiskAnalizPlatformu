package com.riskguard.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document")
public class DocumentEntity {

    @Id
    private UUID id;

    private String filename;
    private String language;

    // Bu belge hangi versiyon grubuna ait?
    // Aynı sözleşmenin 1., 2., 3. revizyonlarını gruplayabilmek için kullanılır.
    @Column(name = "version_group_id")
    private UUID versionGroupId;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String text;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    // Bu metin OCR'dan mı çıktı yoksa doğrudan PDF metninden mi geldi?
    // DocumentService.saveDocument(...) içinde set ediliyor.
    @Column(name = "ocr_used")
    private Boolean ocrUsed;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (uploadedAt == null) {
            uploadedAt = Instant.now();
        }
        // ocrUsed'i burada zorla set etmiyoruz çünkü DocumentService bunu
        // gerçek extraction sonucuna göre set ediyor.
    }

    // ---------- Getters & Setters ----------

    public UUID getId() {
        return id;
    }
    public void setId(UUID id) {
        this.id = id;
    }

    public String getFilename() {
        return filename;
    }
    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getLanguage() {
        return language;
    }
    public void setLanguage(String language) {
        this.language = language;
    }

    public UUID getVersionGroupId() {
        return versionGroupId;
    }
    public void setVersionGroupId(UUID versionGroupId) {
        this.versionGroupId = versionGroupId;
    }

    public String getText() {
        return text;
    }
    public void setText(String text) {
        this.text = text;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public Boolean getOcrUsed() {
        return ocrUsed;
    }
    public void setOcrUsed(Boolean ocrUsed) {
        this.ocrUsed = ocrUsed;
    }
}
