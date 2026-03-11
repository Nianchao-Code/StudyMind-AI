package com.studymind.app.youtube;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Fetches YouTube transcript via StudyMind backend (youtube-transcript-api).
 * Configure TRANSCRIPT_BACKEND_URL in local.properties.
 */
public class TranscriptBackendClient {
    private final String baseUrl;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    private final Gson gson = new Gson();
    private final Executor executor = Executors.newSingleThreadExecutor();

    public TranscriptBackendClient(String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
    }

    public static boolean isConfigured(String url) {
        return url != null && !url.trim().isEmpty();
    }

    public void fetchTranscript(String videoId, TranscriptCallback callback) {
        executor.execute(() -> {
            try {
                String transcript = fetch(videoId);
                if (transcript != null && !transcript.trim().isEmpty()) {
                    callback.onSuccess(transcript.trim());
                } else {
                    callback.onError(new IOException("Backend returned empty transcript"));
                }
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    private String fetch(String videoId) throws IOException {
        String url = baseUrl + "/transcript?video_id=" + videoId;
        Request req = new Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .build();

        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) return null;
            String json = resp.body().string();
            BackendResponse r = gson.fromJson(json, BackendResponse.class);
            return r != null ? r.transcript : null;
        }
    }

    private static class BackendResponse {
        @SerializedName("video_id")
        String videoId;
        @SerializedName("transcript")
        String transcript;
    }

    public interface TranscriptCallback {
        void onSuccess(String transcript);
        void onError(Throwable t);
    }
}
