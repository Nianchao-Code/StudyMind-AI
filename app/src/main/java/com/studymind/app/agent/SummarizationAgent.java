package com.studymind.app.agent;

import com.studymind.app.api.AIApiClient;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates structured exam-ready notes from content.
 * Uses strategy-specific prompts for optimal output format.
 */
public class SummarizationAgent {
    private static final String HIERARCHY_FORMAT = "Use • Main topic\\n  - sub-point\\n  - sub-point for ALL sections. Main topic = bold header, sub-points = details. 2–4 main topics per section, 1–3 sub-points each.";
    private static final String MERGE_RULE = "MERGE RULE—treat as ONE concept, output ONCE: ContentProvider/Content Providers/Content Provider; ContentObserver/Content Observer; ContentResolver/Content Resolver; Content URI/ContentUri. Use canonical form (e.g. Content Provider). ";
    private static final String BASE_SYSTEM = "You are StudyMind AI. Create EXAM-FOCUSED notes—考点总结 (key exam points + summary), NOT generic definitions. "
            + "Rules: NO DUPLICATION—each concept appears ONCE per module. " + MERGE_RULE + "No redundancy. Never use N/A. "
            + "FOCUS: 考点 (testable points)—specific, actionable, exam-relevant. NOT vague like \"allows data sharing\". Include: how it works, key APIs, common exam questions. "
            + "STRUCTURE: " + HIERARCHY_FORMAT + " "
            + "CODE RULE: When including code snippets, ALWAYS add a one-line explanation before or after—what it does, key params. Never paste code without context.\n"
            + "Respond ONLY with valid JSON (use \\n for newlines):\n"
            + "{\"keyDefinitions\":\"...\",\"coreConcepts\":\"...\",\"importantFormulas\":\"...\",\"commonPitfalls\":\"...\",\"quickReview\":\"...\"}\n"
            + "keyDefinitions: Exam-relevant terms. • Term\\n  - specific definition\\n  - exam tip. ONE entry per concept—ContentProvider=Content Provider (merge). ContentObserver=Content Observer (merge).\n"
            + "coreConcepts: Core ideas with 考点. • Concept\\n  - specific mechanism\\n  - exam-frequent point. Merge similar concepts.\n"
            + "importantFormulas: Formulas/code. • Topic\\n  - when to use\\n  - key syntax. For code: snippet + exam context.\n"
            + "commonPitfalls: Exam mistakes. • Category\\n  - specific wrong answer\\n  - correct approach. No generic advice.\n"
            + "quickReview: 考点总结—5–7 distinct concepts max. • Topic\\n  - exam key point\\n  - exam key point. MERGE duplicates (Content Provider + Content Providers = one).";

    private static final String MATH_EXTRA = " Emphasize formulas. Use LaTeX in sub-points.";
    private static final String ALGORITHM_EXTRA = " Emphasize complexity and key steps in sub-points.";
    private static final String CIRCUIT_EXTRA = " Emphasize formulas and units in sub-points.";
    private static final String CONCEPT_EXTRA = " Emphasize definitions. One term per main topic.";
    private static final String CONSOLIDATE_PROMPT = "This is merged content from multiple chunks. DEDUPLICATE—merge ContentProvider/Content Providers/Content Observer/ContentObserver into ONE entry each. Same concept = ONE topic. Remove redundancy. Never use N/A. Output same JSON format.";
    private static final String TRANSCRIPT_EXTRA = "TRANSCRIPT MODE: This is lecture/video transcript. Extract key terms, definitions, and concepts even when explained conversationally. MUST populate ALL 5 sections (keyDefinitions, coreConcepts, importantFormulas, commonPitfalls, quickReview)—don't leave any empty. When the speaker explains a concept, that's a definition. When they mention code, that's in importantFormulas. Same output structure as PDF/text.";

    private final AIApiClient apiClient;

    public SummarizationAgent(AIApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void summarize(String content, ContentAnalysisResult analysisResult, boolean isTranscript, SummarizeCallback callback) {
        summarize(content, analysisResult.getStrategy(), analysisResult.getSubject(), isTranscript, callback);
    }

    public void summarize(String content, SummarizationStrategy strategy, String subject, boolean isTranscript, SummarizeCallback callback) {
        if (content == null || content.trim().isEmpty()) {
            callback.onResult(new StructuredNotes("", "", "", "", "No content to summarize."));
            return;
        }
        if (apiClient == null) {
            callback.onResult(new StructuredNotes("", "", "", "", "API not configured."));
            return;
        }

        String systemPrompt = BASE_SYSTEM + getStrategyExtra(strategy) + (isTranscript ? " " + TRANSCRIPT_EXTRA : "");
        String prompt = "Subject: " + (subject != null ? subject : "General") + "\n\n"
                + "Convert this content into structured exam notes:\n\n" + truncateContent(content);

        apiClient.chat(prompt, systemPrompt, new AIApiClient.ChatCallback() {
            @Override
            public void onSuccess(String response) {
                StructuredNotes notes = parseResponse(response);
                callback.onResult(notes);
            }

            @Override
            public void onError(Throwable t) {
                callback.onError(t);
            }
        });
    }

    private String getStrategyExtra(SummarizationStrategy strategy) {
        if (strategy == null) return "";
        switch (strategy) {
            case MATH_FORMULA: return MATH_EXTRA;
            case ALGORITHM: return ALGORITHM_EXTRA;
            case CIRCUIT_PHYSICS: return CIRCUIT_EXTRA;
            case CONCEPT_THEORY: return CONCEPT_EXTRA;
            default: return "";
        }
    }

    private String truncateContent(String content) {
        int maxLen = 12000;
        if (content == null || content.length() <= maxLen) return content;
        return content.substring(0, maxLen) + "\n\n[Content truncated for length...]";
    }

    private StructuredNotes parseResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return new StructuredNotes("", "", "", "", "");
        }
        // Always try regex extraction first — most robust against truncation and formatting
        StructuredNotes regexResult = extractFieldsFallback(response);
        if (regexResult != null && hasContent(regexResult)) return regexResult;

        try {
            String json = response.trim();
            if (json.contains("```")) {
                int codeStart = json.indexOf("```");
                int jsonStart = json.indexOf("{", codeStart);
                if (jsonStart >= 0) {
                    int codeEnd = json.indexOf("```", jsonStart);
                    if (codeEnd > jsonStart) json = json.substring(jsonStart, codeEnd);
                }
            }
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}') + 1;
            if (start >= 0 && end > start) json = json.substring(start, end);
            json = sanitizeJsonString(json);

            com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            StructuredNotes result = new StructuredNotes(
                    getStr(obj, "keyDefinitions"),
                    getStr(obj, "coreConcepts"),
                    getStr(obj, "importantFormulas"),
                    getStr(obj, "commonPitfalls"),
                    getStr(obj, "quickReview")
            );
            if (hasContent(result)) return result;
        } catch (Exception ignored) {}

        if (regexResult != null) return regexResult;
        return new StructuredNotes("", "", "", "", "");
    }

    private boolean hasContent(StructuredNotes n) {
        return (n.keyDefinitions != null && !n.keyDefinitions.isEmpty())
                || (n.coreConcepts != null && !n.coreConcepts.isEmpty())
                || (n.importantFormulas != null && !n.importantFormulas.isEmpty())
                || (n.quickReview != null && !n.quickReview.isEmpty());
    }

    /** Escape literal newlines/tabs inside JSON string values so Gson can parse them. */
    private String sanitizeJsonString(String json) {
        StringBuilder sb = new StringBuilder(json.length());
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                sb.append(c).append(json.charAt(i + 1));
                i++;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                sb.append(c);
                continue;
            }
            if (inString) {
                if (c == '\n') { sb.append("\\n"); continue; }
                if (c == '\r') continue;
                if (c == '\t') { sb.append("\\t"); continue; }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private StructuredNotes extractFieldsFallback(String response) {
        try {
            String def = extractJsonValue(response, "keyDefinitions");
            String concepts = extractJsonValue(response, "coreConcepts");
            String formulas = extractJsonValue(response, "importantFormulas");
            String pitfalls = extractJsonValue(response, "commonPitfalls");
            String review = extractJsonValue(response, "quickReview");
            if (def != null || concepts != null || formulas != null || pitfalls != null || review != null) {
                return new StructuredNotes(
                        def != null ? def : "",
                        concepts != null ? concepts : "",
                        formulas != null ? formulas : "",
                        pitfalls != null ? pitfalls : "",
                        review != null ? review : ""
                );
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String extractJsonValue(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"", Pattern.DOTALL);
        Matcher m = p.matcher(json);
        if (!m.find()) return null;
        String raw = m.group(1);
        return raw.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\").trim();
    }

    private String getStr(com.google.gson.JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return "";
        return obj.get(key).getAsString().replace("\\n", "\n");
    }

    /** Consolidate merged chunk notes: deduplicate, trim, polish. */
    public void consolidate(StructuredNotes merged, boolean isTranscript, SummarizeCallback callback) {
        if (merged == null || apiClient == null) {
            callback.onResult(merged != null ? merged : new StructuredNotes("", "", "", "", ""));
            return;
        }
        String content = "keyDefinitions:\n" + (merged.keyDefinitions != null ? merged.keyDefinitions : "")
                + "\n\ncoreConcepts:\n" + (merged.coreConcepts != null ? merged.coreConcepts : "")
                + "\n\nimportantFormulas:\n" + (merged.importantFormulas != null ? merged.importantFormulas : "")
                + "\n\ncommonPitfalls:\n" + (merged.commonPitfalls != null ? merged.commonPitfalls : "")
                + "\n\nquickReview:\n" + (merged.quickReview != null ? merged.quickReview : "");
        String sysPrompt = BASE_SYSTEM + (isTranscript ? " " + TRANSCRIPT_EXTRA : "");
        String prompt = CONSOLIDATE_PROMPT + (isTranscript ? " Ensure ALL 5 sections have content." : "") + "\n\n" + content;
        apiClient.chat(prompt, sysPrompt, new AIApiClient.ChatCallback() {
            @Override
            public void onSuccess(String response) {
                StructuredNotes polished = parseResponse(response);
                callback.onResult(polished);
            }
            @Override
            public void onError(Throwable t) {
                callback.onResult(merged);
            }
        });
    }

    public interface SummarizeCallback {
        void onResult(StructuredNotes notes);
        void onError(Throwable t);
    }
}
