package com.studymind.app.agent;

import com.studymind.app.api.AIApiClient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Asks the chat model to suggest 3-5 short tags for a study note. Returns a
 * comma-separated string so it can be stored directly in StudyNote.tags, or
 * an empty string when the model is unavailable or the content is too thin.
 */
public class TagSuggestionAgent {

    private static final String SYSTEM_PROMPT =
            "You are a metadata assistant for a study app. "
            + "Given the note content, reply with 3 to 5 short topical tags that describe the subject "
            + "(e.g. 'algorithms, graphs, dijkstra'). Rules: lowercase, 1-3 words each, comma-separated "
            + "with a single comma and space, no numbering, no quotes, no explanation. "
            + "If the content is too short or generic, reply with exactly: (none)";

    private final AIApiClient apiClient;

    public TagSuggestionAgent(AIApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public interface TagsCallback {
        void onTags(String csv);
    }

    public void suggest(String title, String content, TagsCallback callback) {
        if (apiClient == null) {
            callback.onTags("");
            return;
        }
        String trimmed = content != null ? content.trim() : "";
        if (trimmed.length() < 40) {
            callback.onTags("");
            return;
        }
        String prompt = "Note title: " + (title != null ? title : "(untitled)") + "\n\n"
                + "Note content:\n" + truncate(trimmed, 4000) + "\n\n"
                + "Tags:";
        apiClient.chat(prompt, SYSTEM_PROMPT, new AIApiClient.ChatCallback() {
            @Override
            public void onSuccess(String response) {
                callback.onTags(normalize(response));
            }
            @Override
            public void onError(Throwable t) {
                callback.onTags("");
            }
        });
    }

    /** Normalizes whatever the model returns into "tag-a, tag-b, tag-c" or "". */
    static String normalize(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return "";
        String lower = trimmed.toLowerCase();
        if (lower.equals("(none)") || lower.startsWith("(none")) return "";
        trimmed = trimmed.replaceAll("(?m)^\\s*(Tags?:)\\s*", "");
        trimmed = trimmed.replaceAll("[\\r\\n]+", ",");
        String[] parts = trimmed.split(",");
        Set<String> unique = new LinkedHashSet<>();
        for (String p : parts) {
            String tag = p
                    .replaceAll("^[\\s\\-•\\*\\d.]+", "")
                    .replaceAll("[\\*#\"']", "")
                    .trim()
                    .toLowerCase();
            if (tag.isEmpty()) continue;
            if (tag.length() > 30) tag = tag.substring(0, 30).trim();
            unique.add(tag);
            if (unique.size() >= 5) break;
        }
        if (unique.isEmpty()) return "";
        List<String> list = new ArrayList<>(unique);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    private String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s != null ? s : "";
        return s.substring(0, max) + "\n[...]";
    }
}
