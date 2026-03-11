package com.studymind.app.youtube;

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

    private final TranscriptBackendClient backendClient;
    private final YouTubeTranscriptFetcher transcriptFetcher;
    private final InvidiousCaptionsFetcher invidiousCaptions;
    private final WatchPageTranscriptFetcher watchPageFetcher;

    public YouTubeVideoAnalyzer() {
        this(null);
    }

    /** @param backendUrl TRANSCRIPT_BACKEND_URL from BuildConfig; if set, backend is tried first. */
    public YouTubeVideoAnalyzer(String backendUrl) {
        this.backendClient = TranscriptBackendClient.isConfigured(backendUrl)
                ? new TranscriptBackendClient(backendUrl) : null;
        this.transcriptFetcher = new YouTubeTranscriptFetcher();
        this.invidiousCaptions = new InvidiousCaptionsFetcher();
        this.watchPageFetcher = new WatchPageTranscriptFetcher();
    }

    /**
     * Normalize YouTube URL (fix common typos like ps://, missing https:).
     */
    public static String normalizeUrl(String url) {
        if (url == null || url.trim().isEmpty()) return url;
        String s = url.trim();
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
            fetchVideoTitle(videoId, title -> startTranscriptFlow(videoId, title, callback));
        } else {
            startTranscriptFlow(videoId, null, callback);
        }
    }

    private void startTranscriptFlow(String videoId, String titleHint, VideoAnalysisCallback callback) {
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
                        tryInnertube(videoId, titleHint, callback);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    callback.onProgress("Backend failed: " + t.getMessage());
                    tryInnertube(videoId, titleHint, callback);
                }
            });
            return;
        }
        tryInnertube(videoId, titleHint, callback);
    }

    private void tryInnertube(String videoId, String titleHint, VideoAnalysisCallback callback) {
        callback.onProgress("Step 1/3: YouTube Innertube...");
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
                tryWatchPage(videoId, titleHint, callback);
            }

            @Override
            public void onError(Throwable t) {
                callback.onProgress("Step 1 failed: " + t.getMessage());
                tryWatchPage(videoId, titleHint, callback);
            }
        });
    }

    private void tryWatchPage(String videoId, String titleHint, VideoAnalysisCallback callback) {
        callback.onProgress("Step 2/3: YouTube watch page...");
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
                    tryPiped(videoId, titleHint, callback);
                }
            } catch (Exception e) {
                callback.onProgress("Step 2 failed: " + e.getMessage());
                tryPiped(videoId, titleHint, callback);
            }
        }).start();
    }

    private void tryPiped(String videoId, String titleHint, VideoAnalysisCallback callback) {
        callback.onProgress("Step 3/3: Piped API...");
        invidiousCaptions.fetchTranscript(videoId, new InvidiousCaptionsFetcher.TranscriptCallback() {
            @Override
            public void onSuccess(String transcript) {
                int len = transcript != null ? transcript.trim().length() : 0;
                boolean trusted = isTrustedTranscript(titleHint, transcript);
                callback.onProgress("Step 3 result: Piped → " + len + " chars, trusted=" + trusted);
                if (trusted) {
                    callback.onProgress("Generating notes from Piped...");
                    proceedWithTranscript(titleHint, transcript.trim(), callback, "piped");
                } else {
                    callback.onProgress("All 3 sources failed. Showing Whisper fallback.");
                    callback.onError(new IllegalStateException("No transcript available"));
                }
            }

            @Override
            public void onError(Throwable t) {
                callback.onProgress("Step 3 failed: " + t.getMessage());
                callback.onProgress("All 3 sources failed. Showing Whisper fallback.");
                callback.onError(t);
            }
        });
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
    }
}
