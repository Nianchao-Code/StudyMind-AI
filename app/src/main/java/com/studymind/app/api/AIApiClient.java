package com.studymind.app.api;

/**
 * AI API client interface.
 * Can integrate with OpenAI, Azure, or other compatible LLM services.
 */
public interface AIApiClient {
    /**
     * Send prompt and get LLM response text.
     * @param prompt User prompt
     * @param systemPrompt Optional system prompt, can be null
     * @param callback Result callback
     */
    void chat(String prompt, String systemPrompt, ChatCallback callback);

    interface ChatCallback {
        void onSuccess(String response);
        void onError(Throwable t);
    }
}
