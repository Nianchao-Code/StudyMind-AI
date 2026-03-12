package com.studymind.app.whisper;

import android.content.Context;
import android.net.Uri;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Whisper transcription: backend proxy or direct OpenAI.
 * Supports: mp3, mp4, mpeg, mpga, m4a, wav, webm.
 */
public class WhisperApiClient {
    private static final String API_URL = "https://api.openai.com/v1/audio/transcriptions";
    private static final long MAX_FILE_SIZE = 25 * 1024 * 1024; // 25 MB (OpenAI Whisper limit)

    private final String apiKey;
    private final String backendBaseUrl;
    private final OkHttpClient client;
    private final Gson gson;
    private final Executor executor;

    private static final int CONNECT_TIMEOUT_SEC = 60;
    private static final int WRITE_TIMEOUT_SEC = 120;   // upload large video/audio
    private static final int READ_TIMEOUT_SEC = 300;   // Whisper processes ~1 min per 1 min audio

    public WhisperApiClient(String apiKey) {
        this(apiKey, null);
    }

    /** @param backendBaseUrl When set, uses backend /api/whisper (no API key in APK). */
    public WhisperApiClient(String apiKey, String backendBaseUrl) {
        this.apiKey = apiKey;
        this.backendBaseUrl = backendBaseUrl != null && !backendBaseUrl.trim().isEmpty()
                ? backendBaseUrl.trim().replaceAll("/+$", "") : null;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void transcribe(Context context, Uri audioUri, String fileName, TranscribeCallback callback) {
        executor.execute(() -> {
            try {
                long fileSize = getFileSize(context, audioUri);
                long limit = MAX_FILE_SIZE;
                if (fileSize > limit) {
                    transcribeChunked(context, audioUri, fileName, callback);
                } else {
                    transcribeSingle(context, audioUri, fileName, callback);
                }
            } catch (Exception e) {
                runOnMain(() -> callback.onError(e));
            }
        });
    }

    private long getFileSize(Context context, Uri uri) {
        try (android.content.res.AssetFileDescriptor afd = context.getContentResolver().openAssetFileDescriptor(uri, "r")) {
            return afd != null ? afd.getLength() : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private void transcribeSingle(Context context, Uri audioUri, String fileName, TranscribeCallback callback) {
        try {
            InputStream is = context.getContentResolver().openInputStream(audioUri);
            if (is == null) {
                runOnMain(() -> callback.onError(new IOException("Could not open file")));
                return;
            }
            byte[] bytes = readFullyWithLimit(is, MAX_FILE_SIZE + 1);
            is.close();
            if (bytes == null || bytes.length == 0) {
                runOnMain(() -> callback.onError(new IOException("Empty file")));
                return;
            }
            if (bytes.length > MAX_FILE_SIZE) {
                transcribeChunked(context, audioUri, fileName, callback);
                return;
            }
            String mime = getMimeType(fileName);
            doTranscribe(bytes, fileName, mime, callback);
        } catch (Exception e) {
            runOnMain(() -> callback.onError(e));
        }
    }

    private void transcribeChunked(Context context, Uri audioUri, String fileName, TranscribeCallback callback) {
        java.util.List<java.io.File> chunks = null;
        try {
            chunks = AudioChunker.split(context, audioUri);
            if (chunks == null || chunks.isEmpty()) {
                runOnMain(() -> callback.onError(new IOException("Failed to split audio")));
                return;
            }
            StringBuilder fullTranscript = new StringBuilder();
            for (int i = 0; i < chunks.size(); i++) {
                byte[] chunkBytes = AudioChunker.readFile(chunks.get(i));
                if (chunkBytes.length > MAX_FILE_SIZE) {
                    runOnMain(() -> callback.onError(new IOException("Chunk too large after split")));
                    return;
                }
                String chunkName = "chunk_" + i + ".m4a";
                String text = doTranscribeBlocking(chunkBytes, chunkName, "audio/mp4");
                if (text != null && !text.isEmpty()) {
                    fullTranscript.append(text).append("\n\n");
                }
            }
            runOnMain(() -> callback.onSuccess(fullTranscript.toString().trim()));
        } catch (Exception e) {
            runOnMain(() -> callback.onError(e));
        } finally {
            if (chunks != null) AudioChunker.deleteChunks(chunks);
        }
    }

    private String doTranscribeBlocking(byte[] bytes, String fileName, String mimeType) throws IOException {
        if (backendBaseUrl != null) {
            return doTranscribeViaBackend(bytes, fileName, mimeType);
        }
        RequestBody fileBody = RequestBody.create(bytes, MediaType.parse(mimeType));
        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName != null ? fileName : "audio.m4a", fileBody)
                .addFormDataPart("model", "whisper-1")
                .addFormDataPart("response_format", "json")
                .build();
        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            String bodyStr = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("Whisper API error: " + response.code() + " " + bodyStr);
            }
            WhisperResponse resp = gson.fromJson(bodyStr, WhisperResponse.class);
            return resp != null && resp.text != null ? resp.text.trim() : "";
        }
    }

    private String doTranscribeViaBackend(byte[] bytes, String fileName, String mimeType) throws IOException {
        String b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
        JsonObject body = new JsonObject();
        body.addProperty("audio", b64);
        body.addProperty("filename", fileName != null ? fileName : "audio.m4a");
        body.addProperty("mimeType", mimeType);
        Request request = new Request.Builder()
                .url(backendBaseUrl + "/whisper")
                .post(RequestBody.create(gson.toJson(body), MediaType.parse("application/json")))
                .addHeader("Content-Type", "application/json")
                .build();
        try (Response response = client.newCall(request).execute()) {
            String bodyStr = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("Backend Whisper " + response.code() + ": " + bodyStr);
            }
            WhisperResponse resp = gson.fromJson(bodyStr, WhisperResponse.class);
            return resp != null && resp.text != null ? resp.text.trim() : "";
        }
    }

    private byte[] readFully(InputStream is) throws IOException {
        return readFullyWithLimit(is, -1);
    }

    private byte[] readFullyWithLimit(InputStream is, long maxBytes) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = is.read(buf)) > 0) {
            if (maxBytes > 0 && total + n > maxBytes) {
                n = (int) (maxBytes - total);
                baos.write(buf, 0, n);
                total += n;
                break;
            }
            baos.write(buf, 0, n);
            total += n;
        }
        return baos.toByteArray();
    }

    private String getMimeType(String fileName) {
        if (fileName == null) return "audio/mpeg";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".mp3") || lower.endsWith(".mpeg") || lower.endsWith(".mpga")) return "audio/mpeg";
        if (lower.endsWith(".mp4") || lower.endsWith(".m4a")) return "audio/mp4";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".webm")) return "audio/webm";
        return "audio/mpeg";
    }

    private void doTranscribe(byte[] bytes, String fileName, String mimeType, TranscribeCallback callback) {
        if (backendBaseUrl != null) {
            executor.execute(() -> {
                try {
                    String text = doTranscribeViaBackend(bytes, fileName != null ? fileName : "audio.m4a", mimeType);
                    runOnMain(() -> callback.onSuccess(text != null ? text : ""));
                } catch (Exception e) {
                    runOnMain(() -> callback.onError(e));
                }
            });
            return;
        }
        RequestBody fileBody = RequestBody.create(bytes, MediaType.parse(mimeType));
        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName != null ? fileName : "audio.mp3", fileBody)
                .addFormDataPart("model", "whisper-1")
                .addFormDataPart("response_format", "json")
                .build();

        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnMain(() -> callback.onError(e));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String bodyStr = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    runOnMain(() -> callback.onError(new IOException("Whisper API error: " + response.code() + " " + bodyStr)));
                    return;
                }
                try {
                    WhisperResponse resp = gson.fromJson(bodyStr, WhisperResponse.class);
                    String text = resp != null && resp.text != null ? resp.text.trim() : "";
                    runOnMain(() -> callback.onSuccess(text));
                } catch (Exception e) {
                    runOnMain(() -> callback.onError(e));
                }
            }
        });
    }

    private void runOnMain(Runnable r) {
        executor.execute(r);
    }

    private static class WhisperResponse {
        @SerializedName("text") String text;
    }

    public interface TranscribeCallback {
        void onSuccess(String transcript);
        void onError(Throwable t);
    }
}
