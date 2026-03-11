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

        // Basic auth: token as username (common for "Basic API token")
        String auth = "Basic " + Base64.encodeToString((apiToken + ":").getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        String body = "{\"ids\":[\"" + videoId + "\"]}";

        Request req = new Request.Builder()
                .url(BASE_URL + "/transcripts")
                .addHeader("Authorization", auth)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body, JSON))
                .build();

        try (Response resp = client.newCall(req).execute()) {
            String json = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                throw new IOException("Transcript API " + resp.code() + ": " + (json.length() > 200 ? json.substring(0, 200) + "..." : json));
            }
            String result = parseTranscriptFromResponse(json, videoId);
            if (result == null && !json.trim().isEmpty()) {
                throw new IOException("Transcript API: unexpected response format");
            }
            return result;
        }
    }

    private String parseTranscriptFromResponse(String json, String videoId) {
        try {
            // Root may be array: [{"id":"xxx","transcript":"..."}]
            if (json.trim().startsWith("[")) {
                JsonArray arr = gson.fromJson(json, JsonArray.class);
                if (arr != null && arr.size() > 0) {
                    JsonObject first = arr.get(0).getAsJsonObject();
                    return extractText(first);
                }
                return null;
            }

            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root == null) return null;

            // transcripts array
            if (root.has("transcripts")) {
                JsonArray arr = root.getAsJsonArray("transcripts");
                if (arr != null && arr.size() > 0) {
                    JsonObject first = arr.get(0).getAsJsonObject();
                    return extractText(first);
                }
            }

            // data array
            if (root.has("data")) {
                JsonArray arr = root.getAsJsonArray("data");
                if (arr != null) {
                    for (JsonElement el : arr) {
                        JsonObject obj = el.getAsJsonObject();
                        String id = obj.has("video_id") ? obj.get("video_id").getAsString() : obj.has("id") ? obj.get("id").getAsString() : null;
                        if (videoId.equals(id)) return extractText(obj);
                    }
                }
            }

            // Direct fields
            if (root.has("transcript")) return root.get("transcript").getAsString();
            if (root.has("text")) return root.get("text").getAsString();

            // results array
            if (root.has("results")) {
                JsonArray arr = root.getAsJsonArray("results");
                if (arr != null && arr.size() > 0) {
                    return extractText(arr.get(0).getAsJsonObject());
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String extractText(JsonObject obj) {
        if (obj.has("transcript")) return obj.get("transcript").getAsString();
        if (obj.has("text")) return obj.get("text").getAsString();
        // Segments: [{"text":"..."}]
        if (obj.has("segments")) {
            StringBuilder sb = new StringBuilder();
            for (JsonElement el : obj.getAsJsonArray("segments")) {
                JsonObject s = el.getAsJsonObject();
                if (s.has("text")) sb.append(s.get("text").getAsString()).append(" ");
            }
            return sb.toString().trim();
        }
        return null;
    }

    public interface TranscriptCallback {
        void onSuccess(String transcript);
        void onError(Throwable t);
    }
}
