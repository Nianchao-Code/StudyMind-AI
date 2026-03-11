package com.studymind.app.youtube;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Fetches YouTube transcript using multiple Innertube client types.
 * Tries ANDROID → MWEB → WEB clients in order. ANDROID doesn't need PO tokens.
 */
public class YouTubeTranscriptFetcher {
    private static final String INNERTUBE_WEB = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false";
    private static final String INNERTUBE_ANDROID = "https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w&prettyPrint=false";
    private static final String USER_AGENT_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String USER_AGENT_ANDROID = "com.google.android.youtube/19.09.37 (Linux; U; Android 12; en_US)";
    private static final String CONSENT_COOKIE = "CONSENT=YES+cb; SOCS=CAISNQgDEitib3FfaWRlbnRpdHlmcm9udGVuZHVpc2VydmVyXzIwMjMwODI5LjA3X3AxGgJlbiACGgYIgJnBpwY";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();
    private final Gson gson = new Gson();
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Locale locale;

    public YouTubeTranscriptFetcher() {
        this(Locale.getDefault());
    }

    public YouTubeTranscriptFetcher(Locale locale) {
        this.locale = locale != null ? locale : Locale.getDefault();
    }

    public void fetchTranscript(String videoId, TranscriptCallback callback) {
        executor.execute(() -> {
            StringBuilder log = new StringBuilder();
            try {
                // Try 1: ANDROID client (no PO token needed)
                log.append("ANDROID: ");
                String transcript = fetchViaClient(videoId, "ANDROID", "19.09.37",
                        INNERTUBE_ANDROID, USER_AGENT_ANDROID, 30);
                int len = transcript != null ? transcript.trim().length() : 0;
                log.append(len).append("ch. ");

                // Try 2: MWEB client
                if (len == 0) {
                    log.append("MWEB: ");
                    transcript = fetchViaClient(videoId, "MWEB", "2.20240101.00.00",
                            INNERTUBE_WEB, USER_AGENT_WEB, null);
                    len = transcript != null ? transcript.trim().length() : 0;
                    log.append(len).append("ch. ");
                }

                // Try 3: WEB client (may need PO token but worth trying)
                if (len == 0) {
                    log.append("WEB: ");
                    transcript = fetchViaClient(videoId, "WEB", "2.20240101.00.00",
                            INNERTUBE_WEB, USER_AGENT_WEB, null);
                    len = transcript != null ? transcript.trim().length() : 0;
                    log.append(len).append("ch. ");
                }

                if (transcript != null && !transcript.trim().isEmpty()) {
                    String source = "innertube [" + log + "]";
                    callback.onSuccess(transcript.trim(), source);
                } else {
                    callback.onError(new IOException("All clients returned empty [" + log + "]"));
                }
            } catch (Exception e) {
                callback.onError(new IOException("[" + log + "] " + e.getMessage(), e));
            }
        });
    }

    private String fetchViaClient(String videoId, String clientName, String clientVersion,
                                  String apiUrl, String userAgent, Integer androidSdk) throws IOException {
        JsonObject clientObj = new JsonObject();
        clientObj.addProperty("clientName", clientName);
        clientObj.addProperty("clientVersion", clientVersion);
        clientObj.addProperty("hl", "en");
        clientObj.addProperty("gl", "US");
        if (androidSdk != null) {
            clientObj.addProperty("androidSdkVersion", androidSdk);
        }

        JsonObject context = new JsonObject();
        context.add("client", clientObj);

        JsonObject body = new JsonObject();
        body.add("context", context);
        body.addProperty("videoId", videoId);

        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", userAgent)
                .addHeader("Cookie", CONSENT_COOKIE)
                .post(RequestBody.create(gson.toJson(body), MediaType.parse("application/json")))
                .build();

        try (Response resp = client.newCall(request).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) return null;
            String json = resp.body().string();
            if (json.startsWith("<!") || json.startsWith("<html")) return null;

            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root == null) return null;

            String baseUrl = extractCaptionBaseUrl(root);
            if (baseUrl == null || baseUrl.isEmpty()) return null;

            String transcriptUrl = baseUrl + (baseUrl.contains("?") ? "&" : "?") + "fmt=json3";
            return fetchTranscriptFromUrl(transcriptUrl, userAgent);
        }
    }

    private String extractCaptionBaseUrl(JsonObject root) {
        try {
            JsonObject captions = root.getAsJsonObject("captions");
            if (captions == null) return null;

            JsonObject renderer = captions.getAsJsonObject("playerCaptionsTracklistRenderer");
            if (renderer == null) return null;

            JsonArray tracks = renderer.getAsJsonArray("captionTracks");
            if (tracks == null || tracks.size() == 0) return null;

            String[] langOrder = getLanguageOrder(locale);
            for (String lang : langOrder) {
                if (lang == null || lang.isEmpty()) continue;
                for (JsonElement el : tracks) {
                    JsonObject track = el.getAsJsonObject();
                    String langCode = track.has("languageCode") ? track.get("languageCode").getAsString() : "";
                    if (langCode.isEmpty()) continue;
                    if (langCode.equalsIgnoreCase(lang) || langCode.startsWith(lang) || lang.startsWith(langCode.split("-")[0])) {
                        if (track.has("baseUrl")) return track.get("baseUrl").getAsString();
                    }
                }
            }
            return tracks.get(0).getAsJsonObject().has("baseUrl")
                    ? tracks.get(0).getAsJsonObject().get("baseUrl").getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String fetchTranscriptFromUrl(String url, String userAgent) throws IOException {
        Request req = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", userAgent)
                .addHeader("Cookie", CONSENT_COOKIE)
                .build();

        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) return null;
            String json = resp.body().string();
            return parseJson3Transcript(json);
        }
    }

    private String parseJson3Transcript(String json) {
        if (json == null || json.trim().isEmpty()) return "";
        if (!json.trim().startsWith("{")) return "";
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

    public static String[] getLanguageOrder(Locale locale) {
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
        if (!langs.contains("en-GB")) langs.add("en-GB");
        return langs.toArray(new String[0]);
    }

    public interface TranscriptCallback {
        void onSuccess(String transcript, String source);
        void onError(Throwable t);
    }
}
