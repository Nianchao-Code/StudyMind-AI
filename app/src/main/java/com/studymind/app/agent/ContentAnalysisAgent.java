package com.studymind.app.agent;

import com.studymind.app.api.AIApiClient;

/**
 * Content analysis agent.
 * Analyzes PDF/video text for subject, structure, difficulty, and selects the best summarization strategy.
 */
public class ContentAnalysisAgent {
    private static final String SYSTEM_PROMPT =
            "You are a StudyMind AI content analyzer. Analyze academic content and respond ONLY with a JSON object in this exact format:\n"
            + "{\"subject\":\"...\",\"structure\":\"...\",\"difficulty\":\"introductory|intermediate|advanced\",\"strategy\":\"GENERAL|MATH_FORMULA|ALGORITHM|CIRCUIT_PHYSICS|CONCEPT_THEORY\",\"focusAreas\":\"...\"}\n"
            + "subject: course/subject name (e.g. Data Structures, Linear Algebra).\n"
            + "structure: content type (e.g. lecture slides, textbook, transcript).\n"
            + "strategy: GENERAL for mixed content, MATH_FORMULA for math/formulas, ALGORITHM for algorithms/code, CIRCUIT_PHYSICS for circuits/physics, CONCEPT_THEORY for definitions/theory.\n"
            + "focusAreas: 1-2 sentences on key topics to emphasize.";

    private static final int PREVIEW_LENGTH = 2500;
    private static final int SAMPLE_CHUNK = 800;

    private final AIApiClient apiClient;
    private final AnalysisCache cache;

    public ContentAnalysisAgent(AIApiClient apiClient) {
        this(apiClient, new AnalysisCache());
    }

    public ContentAnalysisAgent(AIApiClient apiClient, AnalysisCache cache) {
        this.apiClient = apiClient;
        this.cache = cache != null ? cache : new AnalysisCache();
    }

    /**
     * Analyze content and return analysis result with recommended strategy.
     * Uses chunk sampling (beginning, middle, end) and in-memory cache.
     * @param fullContent Full text
     * @param callback Result callback
     */
    public void analyze(String fullContent, AnalysisCallback callback) {
        String preview = sampleForAnalysis(fullContent);
        if (preview.isEmpty()) {
            callback.onResult(new ContentAnalysisResult("Unknown", "Unknown", "intermediate",
                    SummarizationStrategy.GENERAL, "General academic content"));
            return;
        }

        String hash = cache.hashContent(preview);
        ContentAnalysisResult cached = cache.get(hash);
        if (cached != null) {
            callback.onResult(cached);
            return;
        }

        if (apiClient == null) {
            callback.onResult(new ContentAnalysisResult("Unknown", "Unknown", "intermediate",
                    SummarizationStrategy.GENERAL, "API client not configured"));
            return;
        }

        String prompt = "Analyze this academic content preview and return the JSON only:\n\n" + preview;

        apiClient.chat(prompt, SYSTEM_PROMPT, new AIApiClient.ChatCallback() {
            @Override
            public void onSuccess(String response) {
                ContentAnalysisResult result = parseResponse(response);
                cache.put(hash, result);
                callback.onResult(result);
            }

            @Override
            public void onError(Throwable t) {
                ContentAnalysisResult fallback = new ContentAnalysisResult("Unknown", "Unknown", "intermediate",
                        SummarizationStrategy.GENERAL, "Analysis failed, using default strategy. " + t.getMessage());
                callback.onResult(fallback);
            }
        });
    }

    /** Sample from beginning, middle, and end of content for better coverage. */
    private String sampleForAnalysis(String content) {
        if (content == null || content.trim().isEmpty()) return "";
        String trimmed = content.trim();
        int len = trimmed.length();
        if (len <= PREVIEW_LENGTH) return trimmed;

        StringBuilder sb = new StringBuilder();
        sb.append(trimmed, 0, Math.min(SAMPLE_CHUNK, len));
        sb.append("\n\n[... middle section ...]\n\n");
        int midStart = (len - SAMPLE_CHUNK) / 2;
        sb.append(trimmed, midStart, Math.min(midStart + SAMPLE_CHUNK, len));
        sb.append("\n\n[... end section ...]\n\n");
        int endStart = Math.max(0, len - SAMPLE_CHUNK);
        sb.append(trimmed, endStart, len);
        return sb.toString();
    }

    private ContentAnalysisResult parseResponse(String response) {
        try {
            // Extract JSON (may be wrapped in markdown)
            String json = response.trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}') + 1;
            if (start >= 0 && end > start) {
                json = json.substring(start, end);
            }

            com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            String subject = getString(obj, "subject", "Unknown");
            String structure = getString(obj, "structure", "Unknown");
            String difficulty = getString(obj, "difficulty", "intermediate");
            String strategyStr = getString(obj, "strategy", "GENERAL");
            String focusAreas = getString(obj, "focusAreas", "");

            SummarizationStrategy strategy;
            try {
                strategy = SummarizationStrategy.valueOf(strategyStr.toUpperCase().replace("-", "_"));
            } catch (Exception e) {
                strategy = SummarizationStrategy.GENERAL;
            }

            return new ContentAnalysisResult(subject, structure, difficulty, strategy, focusAreas);
        } catch (Exception e) {
            return new ContentAnalysisResult("Unknown", "Unknown", "intermediate",
                    SummarizationStrategy.GENERAL, "Parse failed, using default strategy");
        }
    }

    private String getString(com.google.gson.JsonObject obj, String key, String fallback) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return fallback;
    }

    public interface AnalysisCallback {
        void onResult(ContentAnalysisResult result);
        void onError(Throwable t);
    }
}
