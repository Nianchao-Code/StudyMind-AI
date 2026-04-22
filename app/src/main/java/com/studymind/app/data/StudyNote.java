package com.studymind.app.data;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Study note entity for local storage.
 */
@Entity(tableName = "study_notes")
public class StudyNote {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String title;
    public String sourceType;      // "pdf", "youtube", "pasted"
    public String sourceRef;       // file path, video ID, or "pasted"
    public String subject;
    public String strategy;        // SummarizationStrategy name
    public String content;         // Full structured notes JSON or text
    public long createdAt;
    public boolean isPinned;
    public String tags;            // Comma-separated tags
    public byte[] embedding;       // Vector embedding for RAG search (float[] serialized LE)

    public StudyNote() {}

    @Ignore
    public StudyNote(String title, String sourceType, String sourceRef, String subject,
                     String strategy, String content) {
        this.title = title;
        this.sourceType = sourceType;
        this.sourceRef = sourceRef;
        this.subject = subject;
        this.strategy = strategy;
        this.content = content;
        this.createdAt = System.currentTimeMillis();
    }
}
