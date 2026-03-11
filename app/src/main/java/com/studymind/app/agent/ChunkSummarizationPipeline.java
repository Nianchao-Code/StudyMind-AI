package com.studymind.app.agent;

import com.studymind.app.api.AIApiClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Chunk-based summarization: split content into chunks, summarize each, then merge.
 */
public class ChunkSummarizationPipeline {
    private static final int CHUNK_SIZE = 3000;
    private static final int OVERLAP = 200;

    private final AIApiClient apiClient;
    private final ContentAnalysisAgent analysisAgent;
    private final SummarizationAgent summarizationAgent;

    public ChunkSummarizationPipeline(AIApiClient apiClient) {
        this.apiClient = apiClient;
        this.analysisAgent = new ContentAnalysisAgent(apiClient);
        this.summarizationAgent = new SummarizationAgent(apiClient);
    }

    public void process(String content, String title, String sourceType, PipelineCallback callback) {
        if (content == null || content.trim().isEmpty()) {
            callback.onError(new IllegalArgumentException("Empty content"));
            return;
        }
        boolean isTranscript = "youtube".equals(sourceType) || "whisper".equals(sourceType);

        analysisAgent.analyze(content, new ContentAnalysisAgent.AnalysisCallback() {
            @Override
            public void onResult(ContentAnalysisResult analysisResult) {
                List<String> chunks = splitIntoChunks(content);
                if (chunks.size() == 1) {
                    summarizationAgent.summarize(content, analysisResult, isTranscript, new SummarizationAgent.SummarizeCallback() {
                        @Override
                        public void onResult(StructuredNotes notes) {
                            callback.onProgress(chunks.size(), chunks.size());
                            callback.onResult(notes, analysisResult);
                        }
                        @Override
                        public void onError(Throwable t) { callback.onError(t); }
                    });
                    return;
                }

                List<StructuredNotes> chunkNotes = new ArrayList<>();
                AtomicInteger done = new AtomicInteger(0);
                AtomicInteger failed = new AtomicInteger(0);

                for (int i = 0; i < chunks.size(); i++) {
                    final int idx = i;
                    summarizationAgent.summarize(chunks.get(i), analysisResult, isTranscript, new SummarizationAgent.SummarizeCallback() {
                        @Override
                        public void onResult(StructuredNotes notes) {
                            synchronized (chunkNotes) {
                                while (chunkNotes.size() <= idx) chunkNotes.add(null);
                                chunkNotes.set(idx, notes);
                            }
                            int d = done.incrementAndGet();
                            callback.onProgress(d, chunks.size());
                            if (d == chunks.size()) {
                                mergeAndFinish(chunkNotes, analysisResult, isTranscript, callback);
                            }
                        }
                        @Override
                        public void onError(Throwable t) {
                            int f = failed.incrementAndGet();
                            int d = done.incrementAndGet();
                            callback.onProgress(d, chunks.size());
                            if (d == chunks.size()) {
                                if (chunkNotes.stream().anyMatch(n -> n != null)) {
                                    mergeAndFinish(chunkNotes, analysisResult, isTranscript, callback);
                                } else {
                                    callback.onError(t);
                                }
                            }
                        }
                    });
                }
            }
            @Override
            public void onError(Throwable t) {
                callback.onError(t);
            }
        });
    }

    private List<String> splitIntoChunks(String content) {
        List<String> chunks = new ArrayList<>();
        String s = content.trim();
        int pos = 0;
        while (pos < s.length()) {
            int end = Math.min(pos + CHUNK_SIZE, s.length());
            String chunk = s.substring(pos, end);
            chunks.add(chunk);
            pos = end - (end < s.length() ? OVERLAP : 0);
        }
        return chunks;
    }

    private void mergeAndFinish(List<StructuredNotes> chunkNotes, ContentAnalysisResult analysisResult, boolean isTranscript, PipelineCallback callback) {
        StringBuilder defs = new StringBuilder(), concepts = new StringBuilder(), formulas = new StringBuilder();
        StringBuilder pitfalls = new StringBuilder(), review = new StringBuilder();

        for (StructuredNotes n : chunkNotes) {
            if (n == null) continue;
            if (n.keyDefinitions != null && !n.keyDefinitions.isEmpty()) defs.append(n.keyDefinitions).append("\n\n");
            if (n.coreConcepts != null && !n.coreConcepts.isEmpty()) concepts.append(n.coreConcepts).append("\n\n");
            if (n.importantFormulas != null && !n.importantFormulas.isEmpty()) formulas.append(n.importantFormulas).append("\n\n");
            if (n.commonPitfalls != null && !n.commonPitfalls.isEmpty()) pitfalls.append(n.commonPitfalls).append("\n\n");
            if (n.quickReview != null && !n.quickReview.isEmpty()) review.append(n.quickReview).append("\n\n");
        }

        StructuredNotes merged = new StructuredNotes(
                defs.toString().trim(), concepts.toString().trim(), formulas.toString().trim(),
                pitfalls.toString().trim(), review.toString().trim()
        );
        if (chunkNotes.size() > 1) {
            summarizationAgent.consolidate(merged, isTranscript, new SummarizationAgent.SummarizeCallback() {
                @Override
                public void onResult(StructuredNotes polished) {
                    callback.onResult(polished, analysisResult);
                }
                @Override
                public void onError(Throwable t) {
                    callback.onResult(merged, analysisResult);
                }
            });
        } else {
            callback.onResult(merged, analysisResult);
        }
    }

    public interface PipelineCallback {
        void onProgress(int current, int total);
        void onResult(StructuredNotes notes, ContentAnalysisResult analysis);
        void onError(Throwable t);
    }
}
