package com.studymind.app.youtube;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Fallback: fetch YouTube watch page HTML and extract caption baseUrl from ytInitialPlayerResponse.
 * Use when Innertube API fails.
 */
public class WatchPageTranscriptFetcher {
    private static final String WATCH_URL = "https://www.youtube.com/watch?v=%s";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String CONSENT_COOKIE = "CONSENT=YES+cb; SOCS=CAISNQgDEitib3FfaWRlbnRpdHlmcm9udGVuZHVpc2VydmVyXzIwMjMwODI5LjA3X3AxGgJlbiACGgYIgJnBpwY";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build();
    private final Gson gson = new Gson();
    private final Locale locale;

    public WatchPageTranscriptFetcher() {
        this(Locale.getDefault());
    }

    public WatchPageTranscriptFetcher(Locale locale) {
        this.locale = locale != null ? locale : Locale.getDefault();
    }

    public String fetchTranscript(String videoId) throws IOException {
        String html = fetchWatchPage(videoId);
        if (html == null || html.isEmpty()) return null;

        JsonObject playerResponse = extractPlayerResponse(html);
        if (playerResponse == null) return null;

        String baseUrl = extractCaptionBaseUrl(playerResponse);
        if (baseUrl == null || baseUrl.isEmpty()) return null;

        String transcriptUrl = baseUrl + (baseUrl.contains("?") ? "&" : "?") + "fmt=json3";
        return fetchTranscriptFromUrl(transcriptUrl);
    }

    private String fetchWatchPage(String videoId) throws IOException {
        String url = String.format(WATCH_URL, videoId);
        Request req = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept-Language", "en-US,en;q=0.9")
                .addHeader("Cookie", CONSENT_COOKIE)
                .build();

        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) return null;
            return resp.body().string();
        }
    }

    private JsonObject extractPlayerResponse(String html) {
        int idx = html.indexOf("ytInitialPlayerResponse");
        if (idx < 0) return null;

        idx = html.indexOf("{", idx);
        if (idx < 0) return null;

        int start = idx;
        int depth = 1;
        int i = idx + 1;
        int len = html.length();

        while (i < len && depth > 0) {
            char c = html.charAt(i);
            if (c == '"') {
                i++;
                while (i < len) {
                    char d = html.charAt(i);
                    if (d == '\\') {
                        i += 2;
                        continue;
                    }
                    if (d == '"') {
                        i++;
                        break;
                    }
                    i++;
                }
                continue;
            }
            if (c == '{') depth++;
            else if (c == '}') depth--;
            i++;
        }

        if (depth != 0) return null;
        String json = html.substring(start, i);
        try {
            return gson.fromJson(json, JsonObject.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractCaptionBaseUrl(JsonObject playerResponse) {
        try {
            if (!playerResponse.has("captions")) return null;
            JsonObject captions = playerResponse.getAsJsonObject("captions");
            if (captions == null || !captions.has("playerCaptionsTracklistRenderer")) return null;

            JsonObject renderer = captions.getAsJsonObject("playerCaptionsTracklistRenderer");
            if (renderer == null) return null;

            JsonArray tracks = renderer.getAsJsonArray("captionTracks");
            if (tracks == null || tracks.size() == 0) return null;

            String[] langOrder = getLanguageOrder();
            for (String lang : langOrder) {
                if (lang == null || lang.isEmpty()) continue;
                for (JsonElement el : tracks) {
                    JsonObject track = el.getAsJsonObject();
                    String langCode = track.has("languageCode") ? track.get("languageCode").getAsString() : "";
                    if (langCode.isEmpty()) continue;
                    if (langCode.startsWith(lang) || lang.startsWith(langCode.split("-")[0])) {
                        if (track.has("baseUrl")) {
                            return track.get("baseUrl").getAsString();
                        }
                    }
                }
            }
            JsonObject first = tracks.get(0).getAsJsonObject();
            return first.has("baseUrl") ? first.get("baseUrl").getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String[] getLanguageOrder() {
        List<String> langs = new ArrayList<>();
        if (locale != null) {
            String lang = locale.getLanguage();
            if (lang != null && !lang.isEmpty()) {
                langs.add(lang);
                String country = locale.getCountry();
                if (country != null && !country.isEmpty()) {
                    langs.add(lang + "-" + country);
                }
            }
        }
        if (!langs.contains("en")) langs.add("en");
        if (!langs.contains("en-US")) langs.add("en-US");
        return langs.toArray(new String[0]);
    }

    private String fetchTranscriptFromUrl(String url) throws IOException {
        Request req = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Cookie", CONSENT_COOKIE)
                .build();

        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) return null;
            String json = resp.body().string();
            return parseJson3Transcript(json);
        }
    }

    private String parseJson3Transcript(String json) {
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
                        if (!text.isEmpty() && !"\n".equals(text)) {
                            sb.append(text).append(" ");
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return sb.toString().replaceAll("\\s+", " ").trim();
    }
}
