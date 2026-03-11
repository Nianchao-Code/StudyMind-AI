package com.studymind.app.youtube;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Fetches YouTube captions via Piped API (replaces Invidious which has API disabled).
 * Piped is a privacy-friendly YouTube frontend with working public API.
 */
public class InvidiousCaptionsFetcher {
    private static final String[] PIPED_INSTANCES = {
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.leptons.xyz",
            "https://pipedapi.adminforge.de",
            "https://pipedapi-libre.kavin.rocks",
            "https://piped-api.privacy.com.de",
            "https://api.piped.yt",
            "https://pipedapi.drgns.space",
            "https://pipedapi.ducks.party"
    };
    private static final int TIMEOUT = 20;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();
    private final Gson gson = new Gson();
    private final Executor executor = Executors.newSingleThreadExecutor();

    public void fetchTranscript(String videoId, TranscriptCallback callback) {
        executor.execute(() -> {
            try {
                String transcript = fetchFromPiped(videoId);
                if (transcript != null && !transcript.trim().isEmpty()) {
                    callback.onSuccess(transcript.trim());
                } else {
                    callback.onError(new IOException("Piped: no captions found"));
                }
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    private String fetchFromPiped(String videoId) {
        String lang = Locale.getDefault().getLanguage();
        if (lang == null || lang.isEmpty()) lang = "en";

        for (String base : PIPED_INSTANCES) {
            try {
                String url = base + "/streams/" + videoId;
                String json = httpGet(url);
                if (json == null || !json.trim().startsWith("{")) continue;

                JsonObject obj = gson.fromJson(json, JsonObject.class);
                if (obj == null || !obj.has("subtitles")) continue;

                JsonArray subs = obj.getAsJsonArray("subtitles");
                if (subs == null || subs.size() == 0) continue;

                String captionUrl = pickCaptionUrl(subs, lang);
                if (captionUrl == null) continue;

                String content = httpGet(captionUrl);
                if (content == null || content.trim().isEmpty()) continue;

                String parsed = parseSubtitleContent(content);
                if (parsed != null && parsed.trim().length() > 100) return parsed;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String pickCaptionUrl(JsonArray subs, String preferredLang) {
        String fallbackUrl = null;
        for (JsonElement el : subs) {
            JsonObject s = el.getAsJsonObject();
            String code = s.has("code") ? s.get("code").getAsString() : "";
            String url = s.has("url") ? s.get("url").getAsString() : "";
            if (url.isEmpty()) continue;

            if (code.startsWith(preferredLang)) return url;
            if (code.startsWith("en") && fallbackUrl == null) fallbackUrl = url;
            if (fallbackUrl == null) fallbackUrl = url;
        }
        return fallbackUrl;
    }

    private String parseSubtitleContent(String content) {
        if (content == null || content.trim().isEmpty()) return "";
        String trimmed = content.trim();

        if (trimmed.startsWith("<!DOCTYPE") || trimmed.startsWith("<html")) return "";

        // VTT format
        if (trimmed.startsWith("WEBVTT") || trimmed.contains("-->")) {
            StringBuilder sb = new StringBuilder();
            for (String line : content.split("\\r?\\n")) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("WEBVTT") || line.startsWith("NOTE")
                        || line.contains("-->") || line.matches("^\\d+$")) continue;
                line = line.replaceAll("<[^>]+>", "");
                if (!line.isEmpty()) sb.append(line).append(" ");
            }
            return sb.toString().replaceAll("\\s+", " ").trim();
        }

        // TTML format (Piped returns application/ttml+xml)
        if (trimmed.contains("<tt") || trimmed.contains("<p ") || trimmed.contains("<body") || trimmed.contains("<div")) {
            return extractTextFromXml(trimmed);
        }
        // XML format (YouTube transcript, etc.)
        if (trimmed.contains("<text") || trimmed.contains("<transcript>")) {
            return extractTextFromXml(trimmed);
        }

        // JSON3 format
        if (trimmed.startsWith("{")) {
            return parseJson3(trimmed);
        }

        return "";
    }

    private String extractTextFromXml(String xml) {
        return xml.replaceAll("<[^>]+>", " ")
                .replaceAll("&amp;", "&").replaceAll("&lt;", "<").replaceAll("&gt;", ">")
                .replaceAll("&#39;", "'").replaceAll("&quot;", "\"")
                .replaceAll("\\s+", " ").trim();
    }

    private String parseJson3(String json) {
        StringBuilder sb = new StringBuilder();
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root == null) return "";
            JsonArray events = root.getAsJsonArray("events");
            if (events == null) return "";
            for (JsonElement ev : events) {
                JsonObject obj = ev.getAsJsonObject();
                if (!obj.has("segs")) continue;
                JsonArray segs = obj.getAsJsonArray("segs");
                for (JsonElement s : segs) {
                    JsonObject seg = s.getAsJsonObject();
                    if (seg.has("utf8")) {
                        String text = seg.get("utf8").getAsString().trim();
                        if (!text.isEmpty() && !"\n".equals(text)) sb.append(text).append(" ");
                    }
                }
            }
        } catch (Exception ignored) {}
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private String httpGet(String url) {
        try {
            Request req = new Request.Builder().url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .addHeader("Accept", "*/*")
                    .build();
            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) return null;
                return resp.body().string();
            }
        } catch (Exception e) {
            return null;
        }
    }

    public interface TranscriptCallback {
        void onSuccess(String transcript);
        void onError(Throwable t);
    }
}
