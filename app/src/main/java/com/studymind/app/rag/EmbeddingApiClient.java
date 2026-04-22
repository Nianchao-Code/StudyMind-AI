package com.studymind.app.rag;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Talks to the backend /embed endpoint (OpenAI text-embedding-3-small behind it). */
public class EmbeddingApiClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String baseUrl;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    private final Gson gson = new Gson();
    private final Executor executor = Executors.newSingleThreadExecutor();

    public EmbeddingApiClient(String baseUrl) {
        this.baseUrl = (baseUrl != null ? baseUrl.trim() : "").replaceAll("/+$", "");
    }

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isEmpty();
    }

    public void embed(List<String> texts, Callback callback) {
        if (!isConfigured()) {
            callback.onError(new IllegalStateException("Backend URL not configured"));
            return;
        }
        if (texts == null || texts.isEmpty()) {
            callback.onSuccess(new float[0][]);
            return;
        }
        executor.execute(() -> {
            try {
                float[][] vecs = fetch(texts);
                callback.onSuccess(vecs);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    private float[][] fetch(List<String> texts) throws IOException {
        String url = baseUrl + "/embed";
        EmbedRequest reqBody = new EmbedRequest();
        reqBody.texts = texts;
        RequestBody body = RequestBody.create(gson.toJson(reqBody), JSON);
        Request req = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response resp = client.newCall(req).execute()) {
            String json = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                throw new IOException("Embed " + resp.code() + ": "
                        + (json.length() > 200 ? json.substring(0, 200) + "..." : json));
            }
            EmbedResponse r = gson.fromJson(json, EmbedResponse.class);
            if (r == null || r.embeddings == null) return new float[0][];
            float[][] out = new float[r.embeddings.size()][];
            for (int i = 0; i < r.embeddings.size(); i++) {
                List<Float> v = r.embeddings.get(i);
                float[] arr = new float[v.size()];
                for (int j = 0; j < v.size(); j++) arr[j] = v.get(j);
                out[i] = arr;
            }
            return out;
        }
    }

    public interface Callback {
        void onSuccess(float[][] embeddings);
        void onError(Throwable t);
    }

    private static class EmbedRequest {
        List<String> texts;
    }

    private static class EmbedResponse {
        List<List<Float>> embeddings;
    }
}
