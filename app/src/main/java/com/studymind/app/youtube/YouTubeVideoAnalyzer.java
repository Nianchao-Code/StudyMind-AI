package com.studymind.app.youtube;

import android.util.Log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Fetches YouTube transcript and optional metadata (title).
 * Same pattern as PDF/audio: extract content, then MainActivity runs pipeline.
 */
public class YouTubeVideoAnalyzer {
    /** Minimum transcript length; shorter = likely error or wrong content (e.g. 16-min video ~15000+ chars). */
    private static final int MIN_TRANSCRIPT_LENGTH = 300;

    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile(
            "(?:youtube\\.com/watch\\?v=|youtu\\.be/|youtube\\.com/embed/)([a-zA-Z0-9_-]{11})"
    );

    private final TranscriptApiClient transcriptApiClient;
    private final TranscriptBackendClient backendClient;
    private final YouTubeTranscriptFetcher transcriptFetcher;
    private final WatchPageTranscriptFetcher watchPageFetcher;
    private final GeminiYouTubeAnalyzer geminiAnalyzer;

    public YouTubeVideoAnalyzer() {
        this(null, null, null);
    }

    /**
     * @param transcriptApiToken youtube-transcript.io API token (tried first)
     * @param backendUrl TRANSCRIPT_BACKEND_URL (Vercel backend)
     * @param geminiApiKey GEMINI_API_KEY for direct video analysis fallback
     */
    public YouTubeVideoAnalyzer(String transcriptApiToken, String backendUrl, String geminiApiKey) {
        this.transcriptApiClient = TranscriptApiClient.isConfigured(transcriptApiToken)
                ? new TranscriptApiClient(transcriptApiToken) : null;
        this.backendClient = TranscriptBackendClient.isConfigured(backendUrl)
                ? new TranscriptBackendClient(backendUrl) : null;
        this.transcriptFetcher = new YouTubeTranscriptFetcher();
        this.watchPageFetcher = new WatchPageTranscriptFetcher();
        this.geminiAnalyzer = GeminiYouTubeAnalyzer.isConfigured(backendUrl, geminiApiKey)
                ? new GeminiYouTubeAnalyzer(backendUrl, geminiApiKey) : null;
    }

    /**
     * Normalize YouTube URL (fix common typos like ps://, missing https:).
     */
    public static String normalizeUrl(String url) {
        if (url == null || url.trim().isEmpty()) return url;
        String s = url.trim();
        if (s.startsWith("ttps://")) return "h" + s;   // ttps -> https
        if (s.startsWith("ps://")) return "htt" + s;
        if (s.startsWith("ttp://")) return "ht" + s;
        if (s.startsWith("tp://")) return "htt" + s;
        if (!s.startsWith("http://") && !s.startsWith("https://") && s.contains("youtube")) {
            return "https://" + s;
        }
        return s;
    }

    /**
     * Extract video ID from YouTube URL.
     * Supports: youtube.com/watch?v=ID, youtu.be/ID, youtube.com/embed/ID
     */
    public static String extractVideoId(String url) {
        if (url == null || url.trim().isEmpty()) return null;
        String normalized = normalizeUrl(url);
        Matcher m = VIDEO_ID_PATTERN.matcher(normalized);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Analyze a YouTube video: fetch metadata + transcript, then run content analysis.
     */
    public void analyze(String youtubeUrl, VideoAnalysisCallback callback) {
        String videoId = extractVideoId(youtubeUrl);
        if (videoId == null) {
            callback.onError(new IllegalArgumentException("Invalid YouTube URL"));
            return;
        }

        if (YouTubeClient.hasApiKey()) {
            callback.onProgress("Fetching video metadata...");
            fetchVideoTitle(videoId, title -> startTranscriptFlow(youtubeUrl, videoId, title, callback));
        } else {
            startTranscriptFlow(youtubeUrl, videoId, null, callback);
        }
    }

    private static final String TAG = "StudyMind";

    private void startTranscriptFlow(String youtubeUrl, String videoId, String titleHint, VideoAnalysisCallback callback) {
        Log.i(TAG, "startTranscriptFlow: videoId=" + videoId + " transcriptApi=" + (transcriptApiClient != null) + " backend=" + (backendClient != null) + " gemini=" + (geminiAnalyzer != null));
        if (transcriptApiClient != null) {
            callback.onProgress("Fetching transcript (Transcript API)...");
            transcriptApiClient.fetchTranscript(videoId, new TranscriptApiClient.TranscriptCallback() {
                @Override
                public void onSuccess(String transcript) {
                    int len = transcript != null ? transcript.trim().length() : 0;
                    boolean trusted = isTrustedTranscript(titleHint, transcript);
                    callback.onProgress("Transcript API → " + len + " chars, trusted=" + trusted);
                    if (trusted) {
                        callback.onProgress("Generating notes from Transcript API...");
                        proceedWithTranscript(titleHint, transcript.trim(), callback, "transcript-api");
                    } else {
                        tryBackend(youtubeUrl, videoId, titleHint, callback);
                    }
                }
                @Override
                public void onError(Throwable t) {
                    callback.onProgress("Transcript API failed: " + t.getMessage());
                    tryBackend(youtubeUrl, videoId, titleHint, callback);
                }
            });
            return;
        }
        tryBackend(youtubeUrl, videoId, titleHint, callback);
    }

    private void tryBackend(String youtubeUrl, String videoId, String titleHint, VideoAnalysisCallback callback) {
        if (backendClient != null) {
            callback.onProgress("Fetching transcript (backend)...");
            backendClient.fetchTranscript(videoId, new TranscriptBackendClient.TranscriptCallback() {
                @Override
                public void onSuccess(String transcript) {
                    int len = transcript != null ? transcript.trim().length() : 0;
                    boolean trusted = isTrustedTranscript(titleHint, transcript);
                    callback.onProgress("Backend → " + len + " chars, trusted=" + trusted);
                    if (trusted) {
                        callback.onProgress("Generating notes from backend...");
                        proceedWithTranscript(titleHint, transcript.trim(), callback, "backend");
                    } else {
                        tryInnertube(youtubeUrl, videoId, titleHint, callback);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    callback.onProgress("Backend failed: " + t.getMessage());
                    tryInnertube(youtubeUrl, videoId, titleHint, callback);
                }
            });
            return;
        }
        tryInnertube(youtubeUrl, videoId, titleHint, callback);
    }

    private void tryGemini(String youtubeUrl, String titleHint, VideoAnalysisCallback callback) {
        if (geminiAnalyzer == null) {
            Log.w(TAG, "tryGemini: no analyzer configured");
            callback.onProgress("No transcript. Falling back to Whisper (download + transcribe).");
            callback.onError(new IllegalStateException("No transcript available"));
            return;
        }
        Log.i(TAG, "tryGemini: analyzing with Gemini");
        callback.onProgress("Analyzing video with Gemini (no transcript needed)...");
        geminiAnalyzer.analyze(youtubeUrl, titleHint, new GeminiYouTubeAnalyzer.GeminiCallback() {
            @Override
            public void onSuccess(com.studymind.app.agent.StructuredNotes notes, com.studymind.app.agent.ContentAnalysisResult analysis) {
                callback.onGeminiResult(notes, analysis, titleHint != null ? titleHint : "YouTube Video");
            }
            @Override
            public void onError(Throwable t) {
                callback.onProgress("Gemini failed: " + t.getMessage());
                callback.onError(t);
            }
        });
    }

    private void tryInnertube(String youtubeUrl, String videoId, String titleHint, VideoAnalysisCallback callback) {
        callback.onProgress("Step 1/2: YouTube Innertube...");
        transcriptFetcher.fetchTranscript(videoId, new YouTubeTranscriptFetcher.TranscriptCallback() {
            @Override
            public void onSuccess(String transcript, String source) {
                int len = transcript != null ? transcript.trim().length() : 0;
                boolean trusted = isTrustedTranscript(titleHint, transcript);
                callback.onProgress("Step 1 result: " + source + " → " + len + " chars, trusted=" + trusted);
                if (trusted) {
                    callback.onProgress("Generating notes from " + source + "...");
                    proceedWithTranscript(titleHint, transcript.trim(), callback, source);
                    return;
                }
                tryWatchPage(youtubeUrl, videoId, titleHint, callback);
            }

            @Override
            public void onError(Throwable t) {
                callback.onProgress("Step 1 failed: " + t.getMessage());
                tryWatchPage(youtubeUrl, videoId, titleHint, callback);
            }
        });
    }

    private void tryWatchPage(String youtubeUrl, String videoId, String titleHint, VideoAnalysisCallback callback) {
        callback.onProgress("Step 2/2: YouTube watch page...");
        new Thread(() -> {
            try {
                String transcript = watchPageFetcher.fetchTranscript(videoId);
                int len = transcript != null ? transcript.trim().length() : 0;
                boolean trusted = isTrustedTranscript(titleHint, transcript);
                callback.onProgress("Step 2 result: watch-page → " + len + " chars, trusted=" + trusted);
                if (trusted) {
                    callback.onProgress("Generating notes from watch-page...");
                    proceedWithTranscript(titleHint, transcript.trim(), callback, "youtube-watch-page");
                } else {
                    tryGemini(youtubeUrl, titleHint, callback);
                }
            } catch (Exception e) {
                callback.onProgress("Step 2 failed: " + e.getMessage());
                tryGemini(youtubeUrl, titleHint, callback);
            }
        }).start();
    }

    private void proceedWithTranscript(String titleHint, String transcript, VideoAnalysisCallback callback, String source) {
        callback.onResult(new VideoAnalysisResult(titleHint, transcript, source));
    }

    private void fetchVideoTitle(String videoId, TitleCallback callback) {
        YouTubeApiService service = YouTubeClient.getService();
        Call<YouTubeVideoResponse> call = service.getVideo("snippet", videoId, YouTubeClient.getApiKey());

        call.enqueue(new Callback<YouTubeVideoResponse>() {
            @Override
            public void onResponse(Call<YouTubeVideoResponse> call, Response<YouTubeVideoResponse> response) {
                String title = null;
                if (response.isSuccessful() && response.body() != null && response.body().items != null && !response.body().items.isEmpty()) {
                    YouTubeVideoResponse.Item item = response.body().items.get(0);
                    if (item.snippet != null) {
                        title = item.snippet.title;
                    }
                }
                callback.onResult(title);
            }

            @Override
            public void onFailure(Call<YouTubeVideoResponse> call, Throwable t) {
                callback.onResult(null);
            }
        });
    }

    private boolean isTrustedTranscript(String titleHint, String transcript) {
        if (transcript == null) return false;
        String t = transcript.trim();
        if (t.length() < MIN_TRANSCRIPT_LENGTH) return false;
        if (titleHint == null || titleHint.trim().isEmpty()) return true;
        String title = titleHint.toLowerCase();
        String text = t.toLowerCase();
        String[] tokens = title.split("[^a-z0-9]+");
        int meaningful = 0;
        int matches = 0;
        for (String token : tokens) {
            if (token == null || token.length() < 3) continue;
            if (isStopword(token)) continue;
            meaningful++;
            if (text.contains(token)) matches++;
            if (matches >= 2) return true;
        }
        // Allow if title has too few meaningful words but transcript is long enough.
        if (meaningful <= 1) return t.length() >= 2000;
        return matches > 0 && t.length() >= 1200;
    }

    private boolean isStopword(String token) {
        return "the".equals(token) || "and".equals(token) || "for".equals(token) || "with".equals(token)
                || "from".equals(token) || "your".equals(token) || "this".equals(token) || "that".equals(token)
                || "video".equals(token) || "theory".equals(token) || "part".equals(token);
    }

    /** Result: transcript + optional title. Same pattern as PDF/audio - pipeline does the rest. */
    public static class VideoAnalysisResult {
        public final String videoTitle;
        public final String transcript;
        public final String transcriptSource;

        public VideoAnalysisResult(String videoTitle, String transcript, String transcriptSource) {
            this.videoTitle = videoTitle;
            this.transcript = transcript;
            this.transcriptSource = transcriptSource;
        }
    }

    private interface TitleCallback {
        void onResult(String title);
    }

    public interface VideoAnalysisCallback {
        void onResult(VideoAnalysisResult result);
        void onError(Throwable t);
        default void onProgress(String message) {}
        /** Called when Gemini analyzes video directly (no transcript). Override to render notes. */
        default void onGeminiResult(com.studymind.app.agent.StructuredNotes notes,
                                    com.studymind.app.agent.ContentAnalysisResult analysis,
                                    String title) {
            onError(new IllegalStateException("No transcript available"));
        }
    }
}
