package com.studymind.app.youtube;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import com.studymind.app.agent.ContentAnalysisResult;
import com.studymind.app.agent.StructuredNotes;
import com.studymind.app.agent.SummarizationStrategy;

/**
 * Analyzes YouTube videos via Gemini (direct or backend proxy).
 * Fallback when transcript fetching fails.
 */
public class GeminiYouTubeAnalyzer {
    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final String PROMPT = "You are StudyMind AI. Analyze this YouTube video and create EXAM-FOCUSED structured notes. "
            + "Output ONLY valid JSON (use \\n for newlines), no markdown:\n"
            + "{\"keyDefinitions\":\"...\",\"coreConcepts\":\"...\",\"importantFormulas\":\"...\",\"commonPitfalls\":\"...\",\"quickReview\":\"...\"}\n"
            + "keyDefinitions: Exam-relevant terms. • Term\\n  - definition\\n  - exam tip.\n"
            + "coreConcepts: Core ideas with 考点. • Concept\\n  - mechanism\\n  - exam point.\n"
            + "importantFormulas: Formulas/code. • Topic\\n  - when to use\\n  - key syntax.\n"
            + "commonPitfalls: Exam mistakes. • Category\\n  - wrong answer\\n  - correct approach.\n"
            + "quickReview: 考点总结—5–7 concepts. • Topic\\n  - key point.\n"
            + "MUST populate ALL 5 sections. Never use N/A. Use • for main topics and - for sub-points.";

    private final String backendBaseUrl;
    private final String geminiApiKey;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build();
    private final Gson gson = new Gson();
    private final Executor executor = Executors.newSingleThreadExecutor();

    /** @param backendBaseUrl Backend URL (e.g. https://xxx.vercel.app/api); if set, uses backend proxy. */
    public GeminiYouTubeAnalyzer(String backendBaseUrl, String geminiApiKey) {
        this.backendBaseUrl = backendBaseUrl != null && !backendBaseUrl.trim().isEmpty()
                ? backendBaseUrl.trim().replaceAll("/+$", "") : null;
        this.geminiApiKey = geminiApiKey != null && !geminiApiKey.trim().isEmpty()
                ? geminiApiKey.trim() : null;
    }

    public static boolean isConfigured(String backendUrl, String geminiKey) {
        return (backendUrl != null && !backendUrl.trim().isEmpty())
                || (geminiKey != null && !geminiKey.trim().isEmpty());
    }

    public void analyze(String youtubeUrl, String title, GeminiCallback callback) {
        executor.execute(() -> {
            try {
                GeminiResult result = fetch(youtubeUrl, title);
                if (result != null) {
                    callback.onSuccess(result.notes, result.analysis);
                } else {
                    callback.onError(new IllegalStateException("Gemini returned no content"));
                }
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    private GeminiResult fetch(String youtubeUrl, String title) throws Exception {
        if (backendBaseUrl != null) {
            return fetchViaBackend(youtubeUrl, title);
        }
        if (geminiApiKey == null || geminiApiKey.isEmpty()) return null;
        return fetchViaGemini(youtubeUrl, title);
    }

    private GeminiResult fetchViaBackend(String youtubeUrl, String title) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("url", youtubeUrl);
        body.addProperty("title", title != null ? title : "YouTube");
        Request req = new Request.Builder()
                .url(backendBaseUrl + "/gemini_youtube")
                .post(RequestBody.create(gson.toJson(body), JSON))
                .addHeader("Content-Type", "application/json")
                .build();
        try (Response resp = client.newCall(req).execute()) {
            String json = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                throw new IOException("Backend " + resp.code() + ": " + (json.length() > 300 ? json.substring(0, 300) + "..." : json));
            }
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root == null || !root.has("notes")) return null;
            String notesText = root.get("notes").getAsString();
            StructuredNotes notes = parseStructuredNotes(notesText);
            if (notes == null) return null;
            ContentAnalysisResult analysis = new ContentAnalysisResult(
                    "YouTube Video", "video", "general",
                    SummarizationStrategy.CONCEPT_THEORY, ""
            );
            return new GeminiResult(notes, analysis);
        }
    }

    private GeminiResult fetchViaGemini(String youtubeUrl, String title) throws Exception {
        JsonObject root = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject content = new JsonObject();
        JsonArray parts = new JsonArray();

        // Part 1: fileData with YouTube URL (Gemini accepts YouTube URLs directly)
        JsonObject fileDataPart = new JsonObject();
        JsonObject fileData = new JsonObject();
        fileData.addProperty("fileUri", youtubeUrl);
        fileDataPart.add("fileData", fileData);
        parts.add(fileDataPart);

        // Part 2: prompt
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", "Video: " + (title != null ? title : "YouTube") + "\n\n" + PROMPT);
        parts.add(textPart);

        content.add("parts", parts);
        contents.add(content);
        root.add("contents", contents);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", 0.4);
        generationConfig.addProperty("maxOutputTokens", 8192);
        root.add("generationConfig", generationConfig);

        String url = GEMINI_URL + "?key=" + geminiApiKey;
        Request req = new Request.Builder()
                .url(url)
                .post(RequestBody.create(gson.toJson(root), JSON))
                .build();

        try (Response resp = client.newCall(req).execute()) {
            String json = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                throw new IOException("Gemini API " + resp.code() + ": " + (json.length() > 300 ? json.substring(0, 300) + "..." : json));
            }
            GeminiResult result = parseResponse(json);
            if (result == null) {
                throw new IOException("Gemini: failed to parse response");
            }
            return result;
        }
    }

    private GeminiResult parseResponse(String json) {
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root == null || !root.has("candidates")) return null;

            JsonArray candidates = root.getAsJsonArray("candidates");
            if (candidates == null || candidates.size() == 0) return null;

            JsonObject cand = candidates.get(0).getAsJsonObject();
            if (!cand.has("content") || !cand.get("content").getAsJsonObject().has("parts")) return null;

            JsonArray parts = cand.getAsJsonObject("content").getAsJsonArray("parts");
            if (parts == null || parts.size() == 0) return null;

            String text = parts.get(0).getAsJsonObject().get("text").getAsString();
            StructuredNotes notes = parseStructuredNotes(text);
            if (notes == null) return null;

            ContentAnalysisResult analysis = new ContentAnalysisResult(
                    "YouTube Video", "video", "general",
                    SummarizationStrategy.CONCEPT_THEORY, ""
            );
            return new GeminiResult(notes, analysis);
        } catch (Exception e) {
            return null;
        }
    }

    private StructuredNotes parseStructuredNotes(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            String json = text.trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}') + 1;
            if (start >= 0 && end > start) json = json.substring(start, end);
            json = json.replace("\\n", "\n");

            JsonObject obj = gson.fromJson(json, JsonObject.class);
            if (obj == null) return null;

            return new StructuredNotes(
                    getStr(obj, "keyDefinitions"),
                    getStr(obj, "coreConcepts"),
                    getStr(obj, "importantFormulas"),
                    getStr(obj, "commonPitfalls"),
                    getStr(obj, "quickReview")
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String getStr(JsonObject obj, String key) {
        if (!obj.has(key)) return "";
        JsonElement el = obj.get(key);
        return el != null && !el.isJsonNull() ? el.getAsString() : "";
    }

    private static class GeminiResult {
        final StructuredNotes notes;
        final ContentAnalysisResult analysis;

        GeminiResult(StructuredNotes notes, ContentAnalysisResult analysis) {
            this.notes = notes;
            this.analysis = analysis;
        }
    }

    public interface GeminiCallback {
        void onSuccess(StructuredNotes notes, ContentAnalysisResult analysis);
        void onError(Throwable t);
    }
}
