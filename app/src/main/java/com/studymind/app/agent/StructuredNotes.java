package com.studymind.app.agent;

/**
 * Structured exam-mode output format.
 */
public class StructuredNotes {
    public String keyDefinitions;
    public String coreConcepts;
    public String importantFormulas;
    public String commonPitfalls;
    public String quickReview;

    public StructuredNotes() {}

    public StructuredNotes(String keyDefinitions, String coreConcepts, String importantFormulas,
                          String commonPitfalls, String quickReview) {
        this.keyDefinitions = keyDefinitions;
        this.coreConcepts = coreConcepts;
        this.importantFormulas = importantFormulas;
        this.commonPitfalls = commonPitfalls;
        this.quickReview = quickReview;
    }

    public String toDisplayText() {
        StringBuilder sb = new StringBuilder();
        if (keyDefinitions != null && !keyDefinitions.isEmpty()) {
            sb.append("## Key Definitions\n").append(keyDefinitions).append("\n\n");
        }
        if (coreConcepts != null && !coreConcepts.isEmpty()) {
            sb.append("## Core Concepts\n").append(coreConcepts).append("\n\n");
        }
        if (importantFormulas != null && !importantFormulas.isEmpty()) {
            sb.append("## Important Formulas / Steps\n").append(importantFormulas).append("\n\n");
        }
        if (commonPitfalls != null && !commonPitfalls.isEmpty()) {
            sb.append("## Common Exam Pitfalls\n").append(commonPitfalls).append("\n\n");
        }
        if (quickReview != null && !quickReview.isEmpty()) {
            sb.append("## Quick Review\n").append(quickReview);
        }
        return sb.toString().trim();
    }

    public String toJson() {
        return new com.google.gson.Gson().toJson(this);
    }

    public static StructuredNotes fromJson(String json) {
        try {
            return new com.google.gson.Gson().fromJson(json, StructuredNotes.class);
        } catch (Exception e) {
            return null;
        }
    }
}
