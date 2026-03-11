package com.studymind.app.agent;

/**
 * Output of the content analysis agent.
 * Guides the summarization strategy for subsequent chunk processing.
 */
public class ContentAnalysisResult {
    private final String subject;           // e.g. "Data Structures", "Linear Algebra"
    private final String structure;         // e.g. "lecture slides", "textbook excerpt"
    private final String difficulty;        // introductory / intermediate / advanced
    private final SummarizationStrategy strategy;
    private final String focusAreas;       // Suggested focus areas

    public ContentAnalysisResult(String subject, String structure, String difficulty,
                                 SummarizationStrategy strategy, String focusAreas) {
        this.subject = subject;
        this.structure = structure;
        this.difficulty = difficulty;
        this.strategy = strategy;
        this.focusAreas = focusAreas;
    }

    public String getSubject() { return subject; }
    public String getStructure() { return structure; }
    public String getDifficulty() { return difficulty; }
    public SummarizationStrategy getStrategy() { return strategy; }
    public String getFocusAreas() { return focusAreas; }
}
