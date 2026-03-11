package com.studymind.app.api;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Mock client when no API key is configured. Used for testing agent flow.
 * Returns fixed default analysis result.
 */
public class MockAIApiClient implements AIApiClient {
    private final Executor executor = Executors.newSingleThreadExecutor();

    @Override
    public void chat(String prompt, String systemPrompt, ChatCallback callback) {
        executor.execute(() -> {
            try {
                Thread.sleep(500);  // Simulate network delay
                String mockJson = "{\"subject\":\"Sample Content\",\"structure\":\"lecture slides\","
                        + "\"difficulty\":\"intermediate\",\"strategy\":\"GENERAL\","
                        + "\"focusAreas\":\"Mock mode - add OPENAI_API_KEY to local.properties for real analysis.\"}";
                callback.onSuccess(mockJson);
            } catch (InterruptedException e) {
                callback.onError(e);
            }
        });
    }
}
