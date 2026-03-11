package com.studymind.app.agent;

import com.studymind.app.api.AIApiClient;

/**
 * Q&A agent for follow-up questions on generated notes.
 */
public class NoteQAAgent {
    private static final String SYSTEM_PROMPT = "You are a study assistant. The user has study notes below. "
            + "Answer their follow-up question based on these notes. Be concise and exam-focused.";

    private final AIApiClient apiClient;

    public NoteQAAgent(AIApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void ask(String notesContent, String question, QACallback callback) {
        if (apiClient == null) {
            callback.onAnswer("API not configured.");
            return;
        }
        String prompt = "Study notes:\n" + truncate(notesContent, 6000) + "\n\nQuestion: " + question;
        apiClient.chat(prompt, SYSTEM_PROMPT, new AIApiClient.ChatCallback() {
            @Override
            public void onSuccess(String response) {
                callback.onAnswer(response != null ? response.trim() : "");
            }
            @Override
            public void onError(Throwable t) {
                callback.onAnswer("Error: " + t.getMessage());
            }
        });
    }

    private String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s != null ? s : "";
        return s.substring(0, max) + "\n[...]";
    }

    public interface QACallback {
        void onAnswer(String answer);
    }
}
