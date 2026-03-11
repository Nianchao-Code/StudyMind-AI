package com.studymind.app.youtube;

import android.util.Base64;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Fetches YouTube transcript via youtube-transcript.io API.
 * POST /api/transcripts with Basic auth, ids array.
 * Configure TRANSCRIPT_API_TOKEN in local.properties.
 */
public class TranscriptApiClient {
    private static final String BASE_URL = "https://www.youtube-transcript.io/api";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String apiToken;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    private final Gson gson = new Gson();
    private final Executor executor = Executors.newSingleThreadExecutor();

    public TranscriptApiClient(String apiToken) {
        this.apiToken = apiToken != null ? apiToken.trim() : null;
    }

    public static boolean isConfigured(String token) {
        return token != null && !token.trim().isEmpty();
    }

    public void fetchTranscript(String videoId, TranscriptCallback callback) {
        executor.execute(() -> {
            try {
                String transcript = fetch(videoId);
                if (transcript != null && !transcript.trim().isEmpty()) {
                    callback.onSuccess(transcript.trim());
                } else {
                    callback.onError(new IOException("Transcript API returned empty"));
                }
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    private String fetch(String videoId) throws IOException {
        if (apiToken == null || apiToken.isEmpty()) return null;

        String auth = "Basic " + Base64.encodeToString((":" + apiToken).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        String body = "{\"ids\":[\"" + videoId + "\"]}";

        Request req = new Request.Builder()
                .url(BASE_URL + "/transcripts")
                .addHeader("Authorization", auth)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body, JSON))
                .build();

        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) return null;
            String json = resp.body().string();
            return parseTranscriptFromResponse(json, videoId);
        }
    }

    private String parseTranscriptFromResponse(String json, String videoId) {
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root == null) return null;

            // Try transcripts array: [{"id":"xxx","transcript":"..."}]
            if (root.has("transcripts")) {
                JsonArray arr = root.getAsJsonArray("transcripts");
                if (arr != null && arr.size() > 0) {
                    JsonObject first = arr.get(0).getAsJsonObject();
                    if (first.has("transcript")) return first.get("transcript").getAsString();
                    if (first.has("text")) return first.get("text").getAsString();
                }
            }

            // Try data array: [{"video_id":"xxx","transcript":"..."}]
            if (root.has("data")) {
                JsonArray arr = root.getAsJsonArray("data");
                if (arr != null) {
                    for (JsonElement el : arr) {
                        JsonObject obj = el.getAsJsonObject();
                        String id = obj.has("video_id") ? obj.get("video_id").getAsString() : obj.has("id") ? obj.get("id").getAsString() : null;
                        if (videoId.equals(id) && (obj.has("transcript") || obj.has("text"))) {
                            return obj.has("transcript") ? obj.get("transcript").getAsString() : obj.get("text").getAsString();
                        }
                    }
                }
            }

            // Direct transcript field (single video)
            if (root.has("transcript")) return root.get("transcript").getAsString();
            if (root.has("text")) return root.get("text").getAsString();

            // First result in results array
            if (root.has("results")) {
                JsonArray arr = root.getAsJsonArray("results");
                if (arr != null && arr.size() > 0) {
                    JsonObject first = arr.get(0).getAsJsonObject();
                    if (first.has("transcript")) return first.get("transcript").getAsString();
                    if (first.has("text")) return first.get("text").getAsString();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public interface TranscriptCallback {
        void onSuccess(String transcript);
        void onError(Throwable t);
    }
}
