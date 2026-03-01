package com.riskguard.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "clause")
public class ClauseEntity {
    @Id
    private UUID id;
    @Column(name="document_id")
    private UUID documentId;
    private int idx;
    private String heading;
    @Lob
    @Column(columnDefinition = "TEXT")
    private String text;
    private Integer spanStart;
    private Integer spanEnd;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }

    // getters & setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }
    public int getIdx() { return idx; }
    public void setIdx(int idx) { this.idx = idx; }
    public String getHeading() { return heading; }
    public void setHeading(String heading) { this.heading = heading; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public Integer getSpanStart() { return spanStart; }
    public void setSpanStart(Integer spanStart) { this.spanStart = spanStart; }
    public Integer getSpanEnd() { return spanEnd; }
    public void setSpanEnd(Integer spanEnd) { this.spanEnd = spanEnd; }
}
