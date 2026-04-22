package com.studymind.app.agent;

import com.studymind.app.api.AIApiClient;

/**
 * Asks the chat model to produce a short, descriptive title for a study note
 * so users don't have to rely on filename / first-line fallbacks like "msf:33".
 */
public class TitleSuggestionAgent {

    private static final String SYSTEM_PROMPT =
            "You are a study note titler. Given the content below, reply with ONE concise title "
            + "that describes the actual subject (3 to 8 words). Rules: title case, no quotes, no "
            + "trailing punctuation, no 'Note:' / 'Title:' prefix, no emoji. If the content is too "
            + "short or generic to title, reply with exactly: (none)";

    private final AIApiClient apiClient;

    public TitleSuggestionAgent(AIApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public interface TitleCallback {
        /** Returns the suggested title, or empty string when unavailable. */
        void onTitle(String title);
    }

    public void suggest(String content, TitleCallback callback) {
        if (apiClient == null) {
            callback.onTitle("");
            return;
        }
        String trimmed = content != null ? content.trim() : "";
        if (trimmed.length() < 40) {
            callback.onTitle("");
            return;
        }
        String prompt = "Note content:\n" + truncate(trimmed, 3500) + "\n\nTitle:";
        apiClient.chat(prompt, SYSTEM_PROMPT, new AIApiClient.ChatCallback() {
            @Override
            public void onSuccess(String response) {
                callback.onTitle(normalize(response));
            }
            @Override
            public void onError(Throwable t) {
                callback.onTitle("");
            }
        });
    }

    /** Strips quotes, "Title:" prefixes, trailing punctuation, caps length. */
    static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";
        String lower = s.toLowerCase();
        if (lower.equals("(none)") || lower.startsWith("(none")) return "";
        s = s.replaceAll("(?i)^\\s*title\\s*[:\\-]\\s*", "");
        s = s.replaceAll("^[\\s\"'`\\*#]+", "");
        s = s.replaceAll("[\\s\"'`\\*#]+$", "");
        s = s.replaceAll("[.!?]+$", "");
        s = s.split("\\r?\\n", 2)[0].trim();
        if (s.length() > 80) s = s.substring(0, 80).trim();
        return s;
    }

    private String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s != null ? s : "";
        return s.substring(0, max) + "\n[...]";
    }
}
