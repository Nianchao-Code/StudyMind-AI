package com.studymind.app.api;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * AI client that proxies through backend. No API keys in APK.
 */
public class BackendAIApiClient implements AIApiClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String baseUrl;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .build();
    private final Gson gson = new Gson();
    private final Executor executor = Executors.newSingleThreadExecutor();

    public BackendAIApiClient(String baseUrl) {
        this.baseUrl = (baseUrl != null ? baseUrl.trim() : "").replaceAll("/+$", "");
    }

    @Override
    public void chat(String prompt, String systemPrompt, ChatCallback callback) {
        executor.execute(() -> {
            try {
                String response = fetch(prompt, systemPrompt);
                if (response != null) {
                    callback.onSuccess(response);
                } else {
                    callback.onError(new IOException("Backend returned empty"));
                }
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    private String fetch(String prompt, String systemPrompt) throws IOException {
        String url = baseUrl + "/summarize";
        RequestBody body = RequestBody.create(
                gson.toJson(new SummarizeRequest(prompt, systemPrompt)), JSON);
        Request req = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response resp = client.newCall(req).execute()) {
            String json = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                throw new IOException("Backend " + resp.code() + ": " + (json.length() > 200 ? json.substring(0, 200) + "..." : json));
            }
            SummarizeResponse r = gson.fromJson(json, SummarizeResponse.class);
            return r != null ? r.response : null;
        }
    }

    private static class SummarizeRequest {
        String prompt;
        @SerializedName("systemPrompt")
        String systemPrompt;

        SummarizeRequest(String prompt, String systemPrompt) {
            this.prompt = prompt;
            this.systemPrompt = systemPrompt;
        }
    }

    private static class SummarizeResponse {
        String response;
    }
}
