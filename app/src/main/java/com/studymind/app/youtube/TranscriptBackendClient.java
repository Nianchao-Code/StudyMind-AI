package com.studymind.app.youtube;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
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
                    Log.i(TAG, "Backend transcript: success, " + transcript.length() + " chars");
                    callback.onSuccess(transcript.trim());
                } else {
                    Log.w(TAG, "Backend transcript: empty or null");
                    callback.onError(new IOException("Backend returned empty transcript"));
                }
            } catch (Exception e) {
                Log.e(TAG, "Backend transcript error: " + e.getMessage(), e);
                callback.onError(e);
            }
        });
    }

    private static final String TAG = "StudyMind";

    private String fetch(String videoId) throws IOException {
        HttpUrl base = HttpUrl.parse(baseUrl + "/transcript");
        if (base == null) throw new IOException("Invalid backend URL: " + baseUrl);
        String fullUrl = base.newBuilder()
                .addQueryParameter("video_id", videoId)
                .addQueryParameter("url", "https://www.youtube.com/watch?v=" + videoId)
                .build()
                .toString();
        Log.i(TAG, "Backend transcript: GET " + fullUrl);
        Request req = new Request.Builder()
                .url(fullUrl)
                .addHeader("Accept", "application/json")
                .build();

        try (Response resp = client.newCall(req).execute()) {
            int code = resp.code();
            Log.i(TAG, "Backend transcript: HTTP " + code);
            String bodyStr = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                Log.w(TAG, "Backend transcript failed: " + code + " " + bodyStr);
                throw new IOException("Backend HTTP " + code + (bodyStr.isEmpty() ? "" : ": " + (bodyStr.length() > 80 ? bodyStr.substring(0, 80) + "…" : bodyStr)));
            }
            BackendResponse r = gson.fromJson(bodyStr, BackendResponse.class);
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
