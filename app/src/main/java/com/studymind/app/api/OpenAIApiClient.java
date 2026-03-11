package com.studymind.app.api;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * OpenAI-compatible API client implementation.
 * Requires API key in BuildConfig / local.properties.
 * Includes retry with exponential backoff on 429/5xx.
 */
public class OpenAIApiClient implements AIApiClient {
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 1000;

    private final String apiKey;
    private final OkHttpClient client;
    private final Gson gson;
    private final Executor mainExecutor;

    private static final int CONNECT_TIMEOUT_SEC = 60;
    private static final int WRITE_TIMEOUT_SEC = 120;  // large prompts (30min transcript → many chunks)
    private static final int READ_TIMEOUT_SEC = 300;   // consolidate + long summarization can take 3-5 min

    public OpenAIApiClient(String apiKey) {
        this.apiKey = apiKey;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
        this.mainExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    public void chat(String prompt, String systemPrompt, ChatCallback callback) {
        doChat(prompt, systemPrompt, callback, 0);
    }

    private void doChat(String prompt, String systemPrompt, ChatCallback callback, int attempt) {
        List<Message> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(new Message("system", systemPrompt));
        }
        messages.add(new Message("user", prompt));

        ChatRequest request = new ChatRequest("gpt-4o-mini", messages, 4096);
        String json = gson.toJson(request);

        Request httpRequest = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(json, JSON))
                .build();

        client.newCall(httpRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (attempt < MAX_RETRIES) {
                    scheduleRetry(() -> doChat(prompt, systemPrompt, callback, attempt + 1), attempt);
                } else {
                    runOnMain(() -> callback.onError(e));
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                int code = response.code();
                String bodyStr = response.body() != null ? response.body().string() : "";

                if (response.isSuccessful()) {
                    ChatResponse resp = gson.fromJson(bodyStr, ChatResponse.class);
                    String content = resp.choices != null && !resp.choices.isEmpty()
                            ? resp.choices.get(0).message.content
                            : "";
                    runOnMain(() -> callback.onSuccess(content.trim()));
                    return;
                }

                boolean retryable = (code == 429 || code >= 500) && attempt < MAX_RETRIES;
                if (retryable) {
                    scheduleRetry(() -> doChat(prompt, systemPrompt, callback, attempt + 1), attempt);
                } else {
                    String msg = code == 429 ? "Rate limit exceeded. Please wait and try again." : "API error: " + code + " " + bodyStr;
                    runOnMain(() -> callback.onError(new IOException(msg)));
                }
            }
        });
    }

    private void scheduleRetry(Runnable runnable, int attempt) {
        long delayMs = BASE_DELAY_MS * (1L << attempt);
        mainExecutor.execute(() -> {
            try {
                Thread.sleep(delayMs);
                runnable.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private void runOnMain(Runnable r) {
        mainExecutor.execute(r);
    }

    // --- Request/Response DTOs ---
    private static class ChatRequest {
        String model;
        List<Message> messages;
        @SerializedName("max_tokens") int maxTokens;

        ChatRequest(String model, List<Message> messages, int maxTokens) {
            this.model = model;
            this.messages = messages;
            this.maxTokens = maxTokens;
        }
    }

    private static class Message {
        String role;
        String content;

        Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    private static class ChatResponse {
        List<Choice> choices;
    }

    private static class Choice {
        Message message;
    }
}
