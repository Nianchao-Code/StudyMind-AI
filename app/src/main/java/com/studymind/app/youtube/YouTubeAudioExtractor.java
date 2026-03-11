package com.studymind.app.youtube;

import android.content.Context;
import android.net.Uri;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Extracts audio from YouTube via Piped API for Whisper fallback.
 */
public class YouTubeAudioExtractor {
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
    private static final int CONNECT_TIMEOUT = 20;
    private static final int READ_TIMEOUT = 120;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();
    private final Gson gson = new Gson();
    private final Executor executor = Executors.newSingleThreadExecutor();

    public void extractAudio(Context context, String videoId, ExtractCallback callback) {
        executor.execute(() -> {
            try {
                String audioUrl = fetchAudioUrl(videoId);
                if (audioUrl == null) {
                    callback.onError(new IllegalStateException("Could not get audio URL from any source"));
                    return;
                }
                File outFile = downloadToTemp(context, audioUrl, videoId);
                if (outFile == null) {
                    callback.onError(new IllegalStateException("Could not download audio"));
                    return;
                }
                callback.onSuccess(Uri.fromFile(outFile), outFile.getName());
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    private String fetchAudioUrl(String videoId) {
        for (String base : PIPED_INSTANCES) {
            try {
                String url = base + "/streams/" + videoId;
                Request req = new Request.Builder().url(url)
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .addHeader("Accept", "*/*")
                        .build();
                try (Response resp = client.newCall(req).execute()) {
                    if (!resp.isSuccessful() || resp.body() == null) continue;
                    String json = resp.body().string();
                    if (!json.trim().startsWith("{")) continue;

                    JsonObject obj = gson.fromJson(json, JsonObject.class);
                    if (obj == null) continue;

                    String audioOnly = findAudioStream(obj, "audioStreams");
                    if (audioOnly != null) return audioOnly;
                    audioOnly = findAudioStream(obj, "adaptiveFormats");
                    if (audioOnly != null) return audioOnly;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String findAudioStream(JsonObject obj, String key) {
        if (!obj.has(key)) return null;
        JsonArray streams = obj.getAsJsonArray(key);
        if (streams == null) return null;

        String bestUrl = null;
        int bestBitrate = 0;

        for (JsonElement el : streams) {
            JsonObject s = el.getAsJsonObject();
            String streamUrl = s.has("url") ? s.get("url").getAsString() : "";
            if (streamUrl.isEmpty()) continue;

            String mimeType = s.has("mimeType") ? s.get("mimeType").getAsString() : "";
            if (!mimeType.contains("audio")) continue;

            int bitrate = s.has("bitrate") ? s.get("bitrate").getAsInt() : 0;
            if (bitrate > bestBitrate && bitrate <= 320000) {
                bestBitrate = bitrate;
                bestUrl = streamUrl;
            }
        }

        if (bestUrl != null) return bestUrl;

        for (JsonElement el : streams) {
            JsonObject s = el.getAsJsonObject();
            String mimeType = s.has("mimeType") ? s.get("mimeType").getAsString() : "";
            String streamUrl = s.has("url") ? s.get("url").getAsString() : "";
            if (mimeType.contains("audio") && !streamUrl.isEmpty()) return streamUrl;
        }
        return null;
    }

    private File downloadToTemp(Context context, String audioUrl, String videoId) {
        try {
            Request req = new Request.Builder().url(audioUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .addHeader("Accept", "*/*")
                    .build();
            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) return null;
                File dir = context.getCacheDir();
                File out = new File(dir, "yt_audio_" + videoId + ".m4a");
                try (InputStream in = resp.body().byteStream();
                     FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
                }
                return out;
            }
        } catch (Exception e) {
            return null;
        }
    }

    public interface ExtractCallback {
        void onSuccess(Uri audioUri, String fileName);
        void onError(Throwable t);
    }
}
