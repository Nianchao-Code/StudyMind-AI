package com.studymind.app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.studymind.app.agent.ChunkSummarizationPipeline;
import com.studymind.app.agent.ContentAnalysisResult;
import com.studymind.app.agent.StructuredNotes;
import com.studymind.app.data.NoteRepository;
import com.studymind.app.data.StudyNote;
import com.studymind.app.pdf.PdfTextExtractor;
import com.studymind.app.whisper.WhisperApiClient;
import com.studymind.app.youtube.YouTubeVideoAnalyzer;

public class MainActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private TextView progressText;
    private TextView notesResult;
    private View btnSaveNote;

    private LinearLayout recentNotesContainer;
    private TextView recentNotesEmpty;

    private StructuredNotes lastNotes;
    private ContentAnalysisResult lastAnalysis;
    private NoteRepository repository;
    private String lastTitle = "Untitled";
    private String lastSourceType = "pasted";
    private String lastSourceRef = "";
    private int pipelineRequestId = 0;

    private final ActivityResultLauncher<String[]> pdfPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    try {
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (SecurityException ignored) {}
                    processPdf(uri);
                }
            }
    );

    private final ActivityResultLauncher<String[]> audioPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    try {
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (SecurityException ignored) {}
                    processAudio(uri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });
        repository = new NoteRepository(this);
        recentNotesContainer = findViewById(R.id.recentNotesContainer);
        recentNotesEmpty = findViewById(R.id.recentNotesEmpty);

        progressBar = findViewById(R.id.progressBar);
        progressText = findViewById(R.id.progressText);
        notesResult = findViewById(R.id.notesResult);
        btnSaveNote = findViewById(R.id.btnSaveNote);
        findViewById(R.id.btnDiscardNote).setOnClickListener(v -> discardNote());
        TextInputEditText youtubeUrlInput = findViewById(R.id.youtubeUrlInput);
        TextInputEditText pasteInput = findViewById(R.id.pasteInput);

        View btnAnalyzeYouTube = findViewById(R.id.btnAnalyzeYouTube);
        View btnAnalyzePasted = findViewById(R.id.btnAnalyzePasted);

        // Card-based input grid: each card triggers its action or reveals a panel
        findViewById(R.id.cardImportPdf).setOnClickListener(v -> pdfPicker.launch(new String[]{"application/pdf"}));
        findViewById(R.id.cardRecordVoice).setOnClickListener(v -> startVoiceRecording());
        findViewById(R.id.cardImportAudio).setOnClickListener(v -> audioPicker.launch(new String[]{
                "audio/*", "video/*"
        }));
        findViewById(R.id.cardYouTube).setOnClickListener(v -> {
            View panel = findViewById(R.id.youtubeInputPanel);
            View pastePanel = findViewById(R.id.pasteInputPanel);
            pastePanel.setVisibility(View.GONE);
            panel.setVisibility(panel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        });
        findViewById(R.id.cardPasteText).setOnClickListener(v -> {
            View panel = findViewById(R.id.pasteInputPanel);
            View ytPanel = findViewById(R.id.youtubeInputPanel);
            ytPanel.setVisibility(View.GONE);
            panel.setVisibility(panel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        });

        // Hidden buttons kept for compatibility with setButtonsEnabled()
        findViewById(R.id.btnRecordVoice).setOnClickListener(v -> startVoiceRecording());

        btnAnalyzeYouTube.setOnClickListener(v -> runYouTubeAnalysis(youtubeUrlInput));
        btnAnalyzePasted.setOnClickListener(v -> runPastedAnalysis(pasteInput));
        findViewById(R.id.btnSaveNote).setOnClickListener(v -> saveNote());
        findViewById(R.id.btnHistory).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));

        maybeShowOnboarding();
        handleSharedIntent(getIntent(), youtubeUrlInput);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        TextInputEditText youtubeUrlInput = findViewById(R.id.youtubeUrlInput);
        if (youtubeUrlInput != null) handleSharedIntent(intent, youtubeUrlInput);
    }

    /** Handle Share from YouTube: user taps Share → StudyMind AI. */
    private void handleSharedIntent(Intent intent, TextInputEditText youtubeUrlInput) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (TextUtils.isEmpty(sharedText)) sharedText = intent.getStringExtra(Intent.EXTRA_SUBJECT);
        if (TextUtils.isEmpty(sharedText)) return;

        String url = sharedText.trim();
        String videoId = YouTubeVideoAnalyzer.extractVideoId(url);
        if (videoId == null) return;

        url = YouTubeVideoAnalyzer.normalizeUrl(url);
        if (youtubeUrlInput != null) youtubeUrlInput.setText(url);
        runYouTubeAnalysis(youtubeUrlInput);
    }

    private void maybeShowOnboarding() {
        SharedPreferences prefs = getSharedPreferences("studymind", MODE_PRIVATE);
        if (prefs.getBoolean("onboarding_done", false)) return;
        View onboardingView = getLayoutInflater().inflate(R.layout.dialog_onboarding, null);
        TextView title = onboardingView.findViewById(R.id.onboardingTitle);
        TextView subtitle = onboardingView.findViewById(R.id.onboardingSubtitle);
        TextView points = onboardingView.findViewById(R.id.onboardingPoints);

        title.setText("Welcome to StudyMind AI");
        subtitle.setText("Turn long content into exam-ready notes");
        points.setText("• Import PDF or paste text\n"
                + "• Add YouTube URL to extract transcript\n"
                + "• Record voice or import audio/video (Whisper)\n"
                + "• Get 5 modules: definitions, concepts, formulas, pitfalls, quick review\n"
                + "• Use History for Q&A, quiz, and flashcards");

        new MaterialAlertDialogBuilder(this)
                .setView(onboardingView)
                .setPositiveButton("Got it", (d, w) -> prefs.edit().putBoolean("onboarding_done", true).apply())
                .setCancelable(false)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRecentNotes();
    }

    @Override
    protected void onDestroy() {
        if (recording) stopRecording();
        super.onDestroy();
    }

    private void loadRecentNotes() {
        repository.getAll(notes -> runOnUiThread(() -> {
            recentNotesContainer.removeAllViews();
            if (notes == null || notes.isEmpty()) {
                recentNotesEmpty.setVisibility(View.VISIBLE);
                return;
            }
            recentNotesEmpty.setVisibility(View.GONE);
            // Sort by createdAt descending, show up to 3
            notes.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
            int count = Math.min(3, notes.size());
            LayoutInflater inflater = LayoutInflater.from(this);
            for (int i = 0; i < count; i++) {
                com.studymind.app.data.StudyNote note = notes.get(i);
                View row = inflater.inflate(R.layout.item_note, recentNotesContainer, false);
                TextView titleView = row.findViewById(R.id.title);
                TextView subtitleView = row.findViewById(R.id.subtitle);
                titleView.setText(note.title);
                String ago = formatRelativeTime(note.createdAt);
                String src = note.sourceType != null ? note.sourceType.toUpperCase() : "";
                subtitleView.setText(src + " · " + ago);
                row.setOnClickListener(v -> {
                    Intent intent = new Intent(this, NoteDetailActivity.class);
                    intent.putExtra("id", note.id);
                    startActivity(intent);
                });
                recentNotesContainer.addView(row);
            }
        }));
    }

    private String formatRelativeTime(long createdAt) {
        long diff = System.currentTimeMillis() - createdAt;
        long mins = diff / 60000;
        if (mins < 1) return "Just now";
        if (mins < 60) return mins + "m ago";
        long hours = mins / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        if (days == 1) return "Yesterday";
        return days + " days ago";
    }

    /** Used by both Import Audio/Video and Record voice. Both use transcript mode (5 sections, same as PDF). */
    private void processAudio(Uri uri) {
        processAudio(uri, null);
    }

    private void processAudio(Uri uri, String titleOverride) {
        discardNote();
        String backendUrl = com.studymind.app.BuildConfig.TRANSCRIPT_BACKEND_URL;
        String apiKey = com.studymind.app.BuildConfig.OPENAI_API_KEY;
        if ((backendUrl == null || backendUrl.isEmpty()) && (apiKey == null || apiKey.isEmpty())) {
            Toast.makeText(this, "Configure TRANSCRIPT_BACKEND_URL or OPENAI_API_KEY for audio.", Toast.LENGTH_LONG).show();
            return;
        }
        String fileName = uri.getLastPathSegment();
        lastTitle = titleOverride != null ? titleOverride : (fileName != null ? fileName.replaceAll("\\.[a-zA-Z0-9]+$", "") : "Audio");
        setLoading(true, "Transcribing with Whisper…");
        WhisperApiClient whisper = new WhisperApiClient(apiKey != null ? apiKey : "", backendUrl);
        whisper.transcribe(this, uri, fileName, new WhisperApiClient.TranscribeCallback() {
            @Override
            public void onSuccess(String transcript) {
                runOnUiThread(() -> {
                    if (android.text.TextUtils.isEmpty(transcript)) {
                        setLoading(false, null);
                        Toast.makeText(MainActivity.this, "No speech detected in file", Toast.LENGTH_LONG).show();
                        return;
                    }
                    lastSourceType = "whisper";
                    lastSourceRef = uri.toString();
                    setLoading(true, "Generating notes…");
                    runPipeline(transcript, lastTitle, "whisper", uri.toString());
                });
            }
            @Override
            public void onError(Throwable t) {
                runOnUiThread(() -> {
                    setLoading(false, null);
                    Toast.makeText(MainActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void processPdf(Uri uri) {
        discardNote();
        setLoading(true, "Extracting PDF...");
        new Thread(() -> {
            String text = PdfTextExtractor.extract(this, uri);
            runOnUiThread(() -> {
                if (TextUtils.isEmpty(text)) {
                    setLoading(false, null);
                    Toast.makeText(this, "Could not extract text from PDF", Toast.LENGTH_LONG).show();
                    return;
                }
                String fileName = uri.getLastPathSegment();
                lastTitle = fileName != null ? fileName.replace(".pdf", "") : "PDF Document";
                setLoading(true, "Generating notes…");
                runPipeline(text, lastTitle, "pdf", uri.toString());
            });
        }).start();
    }

    private static final String PREF_YOUTUBE_REMEMBER_VISUAL = "youtube_remember_visual_choice";
    private static final String PREF_YOUTUBE_PREFER_GEMINI = "youtube_prefer_gemini";

    private void runYouTubeAnalysis(TextInputEditText input) {
        String url = input != null && input.getText() != null ? input.getText().toString().trim() : "";
        if (TextUtils.isEmpty(url)) {
            Toast.makeText(this, "Enter a YouTube URL", Toast.LENGTH_SHORT).show();
            return;
        }
        url = YouTubeVideoAnalyzer.normalizeUrl(url);
        if (input != null && input.getText() != null && !url.equals(input.getText().toString().trim())) {
            input.setText(url);
        }
        String videoId = YouTubeVideoAnalyzer.extractVideoId(url);
        if (videoId == null) {
            Toast.makeText(this, "Invalid YouTube URL", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("studymind", MODE_PRIVATE);
        if (prefs.getBoolean(PREF_YOUTUBE_REMEMBER_VISUAL, false)) {
            boolean preferGemini = prefs.getBoolean(PREF_YOUTUBE_PREFER_GEMINI, false);
            startYouTubeAnalysisWithChoice(url, videoId, input, preferGemini);
            return;
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_youtube_visual_choice, null);
        TextView msg = dialogView.findViewById(R.id.dialogMessage);
        msg.setText("Does this video have formulas, diagrams, or charts?\n\nAnalyzing visuals gives better results for STEM content.");
        com.google.android.material.checkbox.MaterialCheckBox rememberCb = dialogView.findViewById(R.id.rememberChoice);
        TextView hint = dialogView.findViewById(R.id.dialogHint);
        hint.setText("You can change this later in Settings → YouTube.");

        final String finalUrl = url;
        final String finalVideoId = videoId;
        final TextInputEditText finalInput = input;
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("YouTube analysis")
                .setView(dialogView)
                .setCancelable(true)
                .create();

        dialogView.findViewById(R.id.optionTranscript).setOnClickListener(v -> {
            if (rememberCb.isChecked()) {
                prefs.edit().putBoolean(PREF_YOUTUBE_REMEMBER_VISUAL, true)
                        .putBoolean(PREF_YOUTUBE_PREFER_GEMINI, false).apply();
            }
            dialog.dismiss();
            startYouTubeAnalysisWithChoice(finalUrl, finalVideoId, finalInput, false);
        });
        dialogView.findViewById(R.id.optionVisuals).setOnClickListener(v -> {
            if (rememberCb.isChecked()) {
                prefs.edit().putBoolean(PREF_YOUTUBE_REMEMBER_VISUAL, true)
                        .putBoolean(PREF_YOUTUBE_PREFER_GEMINI, true).apply();
            }
            dialog.dismiss();
            startYouTubeAnalysisWithChoice(finalUrl, finalVideoId, finalInput, true);
        });
        dialog.show();
    }

    private void startYouTubeAnalysisWithChoice(String url, String videoId, TextInputEditText input, boolean preferGemini) {
        discardNote();
        setLoading(true, preferGemini ? "Analyzing video with AI..." : "Fetching transcript (YouTube)...");
        String transcriptApiToken = com.studymind.app.BuildConfig.TRANSCRIPT_API_TOKEN;
        String backendUrl = com.studymind.app.BuildConfig.TRANSCRIPT_BACKEND_URL;
        String geminiApiKey = com.studymind.app.BuildConfig.GEMINI_API_KEY;
        Log.i("StudyMind", "YouTube analysis: videoId=" + videoId + " preferGemini=" + preferGemini);
        YouTubeVideoAnalyzer analyzer = new YouTubeVideoAnalyzer(transcriptApiToken, backendUrl, geminiApiKey);
        analyzer.analyze(url, preferGemini, new YouTubeVideoAnalyzer.VideoAnalysisCallback() {
            @Override
            public void onProgress(String message) {
                runOnUiThread(() -> setLoading(true, message));
            }

            @Override
            public void onResult(YouTubeVideoAnalyzer.VideoAnalysisResult result) {
                runOnUiThread(() -> {
                    String transcript = result.transcript;
                    if (TextUtils.isEmpty(transcript)) {
                        runWhisperFallbackOrPromptPaste(videoId, null);
                        return;
                    }
                    lastTitle = result.videoTitle != null ? result.videoTitle : "YouTube Video";
                    lastSourceType = "youtube";
                    lastSourceRef = videoId;
                    String preview = transcript.length() > 200 ? transcript.substring(0, 200) + "..." : transcript;
                    setLoading(true, "Preview: " + preview);
                    new android.os.Handler().postDelayed(() -> {
                        setLoading(true, "Generating notes (" + transcript.length() + " chars)…");
                        runPipeline(transcript, lastTitle, "youtube", videoId);
                    }, 3000);
                });
            }

            @Override
            public void onGeminiResult(StructuredNotes notes, ContentAnalysisResult analysis, String title) {
                runOnUiThread(() -> {
                    setLoading(false, null);
                    lastNotes = notes;
                    lastAnalysis = analysis;
                    lastTitle = title;
                    renderModularNotes(notes);
                    View saveContainer = findViewById(R.id.saveButtonsContainer);
                    if (saveContainer != null) saveContainer.setVisibility(View.VISIBLE);
                    lastSourceType = "gemini";
                    lastSourceRef = videoId;
                });
            }

            @Override
            public void onError(Throwable t) {
                runOnUiThread(() -> runWhisperFallbackOrPromptPaste(videoId, t));
            }
        });
    }

    /** When transcript fails: prompt user to download video themselves or paste manually. */
    private void runWhisperFallbackOrPromptPaste(String videoId, Throwable lastError) {
        setLoading(false, null);
        String errMsg = lastError != null ? lastError.getMessage() : "";
        String msg = "Could not get transcript or analyze this video.";
        if (errMsg != null && !errMsg.isEmpty()) {
            msg += "\n\nError: " + (errMsg.length() > 150 ? errMsg.substring(0, 150) + "…" : errMsg);
            if (errMsg.toLowerCase().contains("gemini") && errMsg.toLowerCase().contains("configure")) {
                msg += "\n\nTip: Add GEMINI_API_KEY in Vercel → Settings → Environment Variables.";
            }
        }
        msg += "\n\nPlease download the video yourself and import it via Import Audio/Video, or paste the transcript below.";
        new MaterialAlertDialogBuilder(this)
                .setTitle("No transcript available")
                .setMessage(msg)
                .setPositiveButton("Paste transcript", (d, w) -> {
                    TextInputEditText pasteInput = findViewById(R.id.pasteInput);
                    if (pasteInput != null) pasteInput.requestFocus();
                    Toast.makeText(this, "Paste the video transcript below, then tap Generate Notes.", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("OK", null)
                .show();
    }

    private void runPastedAnalysis(TextInputEditText input) {
        discardNote();
        String text = input != null && input.getText() != null ? input.getText().toString().trim() : "";
        if (TextUtils.isEmpty(text)) {
            Toast.makeText(this, "Paste some content first", Toast.LENGTH_SHORT).show();
            return;
        }
        String videoId = YouTubeVideoAnalyzer.extractVideoId(text);
        if (videoId != null) {
            TextInputEditText youtubeInput = findViewById(R.id.youtubeUrlInput);
            if (youtubeInput != null) youtubeInput.setText(text);
            Toast.makeText(this, "YouTube URL detected. Use Analyze Video above.", Toast.LENGTH_LONG).show();
            return;
        }
        if (text.length() < 50) {
            Toast.makeText(this, "Content too short. Add more text for better results.", Toast.LENGTH_SHORT).show();
            return;
        }
        lastTitle = "Pasted Content";
        lastSourceType = "pasted";
        lastSourceRef = "pasted";
        setLoading(true, "Generating notes…");
        runPipeline(text, lastTitle, "pasted", "pasted");
    }

    private void runPipeline(String content, String title, String sourceType, String sourceRef) {
        if (StudyMindApp.getAIApiClient() instanceof com.studymind.app.api.MockAIApiClient) {
            setLoading(false, null);
            Toast.makeText(this, "Configure TRANSCRIPT_BACKEND_URL or OPENAI_API_KEY in local.properties.", Toast.LENGTH_LONG).show();
            return;
        }
        pipelineRequestId++;
        final int currentRequestId = pipelineRequestId;
        lastSourceType = sourceType;
        lastSourceRef = sourceRef;
        ChunkSummarizationPipeline pipeline = new ChunkSummarizationPipeline(StudyMindApp.getAIApiClient());
        pipeline.process(content, title, sourceType, new ChunkSummarizationPipeline.PipelineCallback() {
            @Override
            public void onProgress(int current, int total) {
                runOnUiThread(() -> setLoading(true, "Analyzing chunk " + current + "/" + total + "…"));
            }

            @Override
            public void onResult(StructuredNotes notes, ContentAnalysisResult analysis) {
                runOnUiThread(() -> {
                    if (currentRequestId != pipelineRequestId) return;
                    setLoading(false, null);
                    lastNotes = notes;
                    lastAnalysis = analysis;
                    lastTitle = title;
                    renderModularNotes(notes);
                    View saveContainer = findViewById(R.id.saveButtonsContainer);
                    if (saveContainer != null) saveContainer.setVisibility(View.VISIBLE);
                    if ("pasted".equals(lastSourceType)) {
                        TextInputEditText pasteInput = findViewById(R.id.pasteInput);
                        if (pasteInput != null) pasteInput.getText().clear();
                    }
                });
            }

            @Override
            public void onError(Throwable t) {
                runOnUiThread(() -> {
                    setLoading(false, null);
                    Toast.makeText(MainActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void discardNote() {
        lastNotes = null;
        lastAnalysis = null;
        LinearLayout sections = findViewById(R.id.notesSectionsContainer);
        if (sections != null) {
            sections.removeAllViews();
            sections.setVisibility(View.GONE);
        }
        notesResult.setVisibility(View.GONE);
        notesResult.setText("");
        View saveContainer = findViewById(R.id.saveButtonsContainer);
        if (saveContainer != null) saveContainer.setVisibility(View.GONE);
    }

    private String cleanSectionContent(String s) {
        if (s == null) return "";
        s = s.trim();
        // Remove markdown that breaks bullet parsing (**bold**, ## headers)
        s = s.replaceAll("\\*\\*([^*]+)\\*\\*", "$1");
        s = s.replaceAll("(?m)^#+\\s*", "");
        // Remove leading N/A
        s = s.replaceFirst("^(?i)N/A\\s*([-:]\\s*[^\\n]*)?[\\n\\r]*", "");
        // Remove N/A on its own line anywhere in content
        s = s.replaceAll("(?m)^\\s*N/A\\s*([-:][^\\n]*)?\\s*[\\r\\n]*", "");
        return s.trim().replaceAll("\\n{3,}", "\n\n");
    }

    private void renderModularNotes(StructuredNotes notes) {
        LinearLayout container = findViewById(R.id.notesSectionsContainer);
        if (container == null) return;
        container.removeAllViews();
        container.setVisibility(View.VISIBLE);
        notesResult.setVisibility(View.GONE);
        String[] titles = {"Key Definitions", "Core Concepts", "Formulas & Steps", "Common Pitfalls", "Quick Review"};
        String[] contents = {notes.keyDefinitions, notes.coreConcepts, notes.importantFormulas, notes.commonPitfalls, notes.quickReview};
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < titles.length; i++) {
            String cleaned = cleanSectionContent(contents[i]);
            if (cleaned.isEmpty()) continue;
            View card = inflater.inflate(R.layout.item_note_section, container, false);
            TextView titleView = card.findViewById(R.id.sectionTitle);
            LinearLayout contentContainer = card.findViewById(R.id.sectionContentContainer);
            TextView chevron = card.findViewById(R.id.sectionChevron);
            titleView.setText(titles[i]);
            populateSectionContent(contentContainer, cleaned, inflater);
            card.setOnClickListener(v -> {
                boolean expanded = contentContainer.getVisibility() == View.VISIBLE;
                contentContainer.setVisibility(expanded ? View.GONE : View.VISIBLE);
                chevron.setText(expanded ? "▶" : "▼");
            });
            container.addView(card);
        }
    }

    private void populateSectionContent(LinearLayout container, String content, LayoutInflater inflater) {
        container.removeAllViews();
        java.util.List<android.util.Pair<String, java.util.List<String>>> topicBlocks = deduplicateTopicBlocks(parseTopicBlocks(content));
        if (!topicBlocks.isEmpty()) {
            for (android.util.Pair<String, java.util.List<String>> block : topicBlocks) {
                View blockView = inflater.inflate(R.layout.item_note_topic_block, container, false);
                TextView topicTitle = blockView.findViewById(R.id.topicTitle);
                LinearLayout subContainer = blockView.findViewById(R.id.subPointsContainer);
                topicTitle.setText(block.first);
                for (String sub : block.second) {
                    TextView subView = (TextView) inflater.inflate(R.layout.item_note_sub_point, subContainer, false);
                    subView.setText("• " + sub);
                    subContainer.addView(subView);
                }
                if (block.second.isEmpty()) subContainer.setVisibility(View.GONE);
                container.addView(blockView);
            }
        } else {
            java.util.List<String> items = parseBulletItems(content);
            if (items.size() > 1) {
                for (String item : items) {
                    String text = item.trim().replaceFirst("^[•\\-]\\s*", "").replaceFirst("^\\d+\\.\\s*", "").trim();
                    View row = inflater.inflate(R.layout.item_note_bullet, container, false);
                    ((TextView) row.findViewById(R.id.bulletText)).setText(text);
                    container.addView(row);
                }
            } else {
                TextView fallback = new TextView(this);
                fallback.setText(content);
                fallback.setTextSize(14);
                fallback.setLineSpacing(fallback.getLineSpacingExtra(), 1.35f);
                fallback.setTextColor(ContextCompat.getColor(this, R.color.leetcode_text));
                int pad = (int) (10 * getResources().getDisplayMetrics().density);
                fallback.setPadding(0, pad, 0, pad);
                container.addView(fallback);
            }
        }
    }

    private java.util.List<android.util.Pair<String, java.util.List<String>>> parseTopicBlocks(String content) {
        java.util.List<android.util.Pair<String, java.util.List<String>>> result = new java.util.ArrayList<>();
        String currentTopic = null;
        java.util.List<String> currentSubs = new java.util.ArrayList<>();
        for (String line : content.split("\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            boolean isIndented = line.startsWith("  ") || line.startsWith("\t");
            if (isIndented && currentTopic != null) {
                currentSubs.add(trimmed.replaceFirst("^[\\-•]\\s*", "").replaceFirst("^\\d+\\.\\s*", "").trim());
            } else if (trimmed.startsWith("•") || trimmed.startsWith("- ") || trimmed.matches("^\\d+\\.\\s+.*")) {
                if (currentTopic != null) {
                    result.add(android.util.Pair.create(currentTopic, new java.util.ArrayList<>(currentSubs)));
                }
                currentTopic = trimmed.replaceFirst("^[•\\-]\\s*", "").replaceFirst("^\\d+\\.\\s*", "").trim();
                currentSubs = new java.util.ArrayList<>();
            }
        }
        if (currentTopic != null) {
            result.add(android.util.Pair.create(currentTopic, currentSubs));
        }
        return result;
    }

    private java.util.List<android.util.Pair<String, java.util.List<String>>> deduplicateTopicBlocks(
            java.util.List<android.util.Pair<String, java.util.List<String>>> blocks) {
        java.util.Map<String, android.util.Pair<String, java.util.List<String>>> seen = new java.util.LinkedHashMap<>();
        for (android.util.Pair<String, java.util.List<String>> block : blocks) {
            String key = block.first.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
            if (key.isEmpty()) continue;
            if (seen.containsKey(key)) {
                android.util.Pair<String, java.util.List<String>> existing = seen.get(key);
                java.util.List<String> mergedSubs = mergeSubs(existing.second, block.second);
                seen.put(key, android.util.Pair.create(existing.first, mergedSubs));
            } else if (key.length() > 1 && key.endsWith("s") && seen.containsKey(key.substring(0, key.length() - 1))) {
                String singular = key.substring(0, key.length() - 1);
                android.util.Pair<String, java.util.List<String>> existing = seen.get(singular);
                java.util.List<String> mergedSubs = mergeSubs(existing.second, block.second);
                seen.put(singular, android.util.Pair.create(existing.first, mergedSubs));
            } else if (seen.containsKey(key + "s")) {
                android.util.Pair<String, java.util.List<String>> existing = seen.remove(key + "s");
                java.util.List<String> mergedSubs = mergeSubs(existing.second, block.second);
                seen.put(key, android.util.Pair.create(block.first, mergedSubs));
            } else {
                seen.put(key, block);
            }
        }
        return new java.util.ArrayList<>(seen.values());
    }

    private java.util.List<String> mergeSubs(java.util.List<String> a, java.util.List<String> b) {
        java.util.List<String> out = new java.util.ArrayList<>(a);
        for (String sub : b) {
            String subNorm = sub.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
            boolean dup = false;
            for (String s : out) {
                if (s.replaceAll("[^a-zA-Z0-9]", "").toLowerCase().equals(subNorm)) { dup = true; break; }
            }
            if (!dup) out.add(sub);
        }
        return out;
    }

    private java.util.List<String> parseBulletItems(String content) {
        java.util.List<String> items = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : content.split("\\n")) {
            String trimmed = line.trim();
            boolean isBullet = trimmed.startsWith("•") || trimmed.startsWith("- ")
                    || trimmed.matches("^\\d+\\.\\s+.*");
            if (isBullet && current.length() > 0) {
                items.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(line).append("\n");
        }
        if (current.length() > 0) items.add(current.toString().trim());
        if (items.size() <= 1) {
            String[] paras = content.trim().split("\\n\\s*\\n");
            if (paras.length > 1) {
                items.clear();
                for (String p : paras) {
                    String s = p.trim();
                    if (!s.isEmpty()) items.add(s);
                }
            }
        }
        if (items.size() <= 1 && content.contains("•")) {
            String[] parts = content.split("\\s*•\\s*");
            if (parts.length > 1) {
                items.clear();
                for (String p : parts) {
                    String s = p.trim().replaceFirst("^\\d+\\.\\s*", "");
                    if (!s.isEmpty()) items.add(s);
                }
            }
        }
        return items;
    }

    private void saveNote() {
        if (lastNotes == null || lastAnalysis == null) return;
        TextInputEditText titleInput = new TextInputEditText(this);
        titleInput.setPadding(48, 32, 48, 32);
        titleInput.setText(lastTitle);
        titleInput.setHint("Note title");
        new MaterialAlertDialogBuilder(this)
                .setTitle("Save to History")
                .setView(titleInput)
                .setPositiveButton("Save", (d, w) -> {
                    String title = titleInput.getText() != null ? titleInput.getText().toString().trim() : "";
                    if (title.isEmpty()) title = lastTitle;
                    StudyNote note = new StudyNote(title, lastSourceType, lastSourceRef,
                            lastAnalysis.getSubject(), lastAnalysis.getStrategy().name(), lastNotes.toJson());
                    repository.insert(note, id -> runOnUiThread(() -> {
                        Toast.makeText(this, "Saved to history", Toast.LENGTH_SHORT).show();
                        discardNote();
                    }));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setLoading(boolean loading, String message) {
        View progressContainer = findViewById(R.id.progressContainer);
        if (progressContainer != null) {
            progressContainer.setVisibility(loading ? View.VISIBLE : View.GONE);
            if (loading) {
                progressContainer.post(() -> {
                    android.widget.ScrollView scroll = findViewById(R.id.mainScroll);
                    if (scroll != null) scroll.smoothScrollTo(0, progressContainer.getTop());
                });
            }
        }
        if (progressText != null) {
            progressText.setVisibility(loading && message != null ? View.VISIBLE : View.GONE);
            if (message != null) progressText.setText(message);
        }
        setButtonsEnabled(!loading);
    }

    private void setButtonsEnabled(boolean enabled) {
        // Card-based input grid
        findViewById(R.id.cardImportPdf).setEnabled(enabled);
        findViewById(R.id.cardImportPdf).setAlpha(enabled ? 1f : 0.5f);
        findViewById(R.id.cardRecordVoice).setEnabled(enabled);
        findViewById(R.id.cardRecordVoice).setAlpha(enabled ? 1f : 0.5f);
        findViewById(R.id.cardImportAudio).setEnabled(enabled);
        findViewById(R.id.cardImportAudio).setAlpha(enabled ? 1f : 0.5f);
        findViewById(R.id.cardYouTube).setEnabled(enabled);
        findViewById(R.id.cardYouTube).setAlpha(enabled ? 1f : 0.5f);
        findViewById(R.id.cardPasteText).setEnabled(enabled);
        findViewById(R.id.cardPasteText).setAlpha(enabled ? 1f : 0.5f);
        // Hidden legacy buttons + input panel buttons
        findViewById(R.id.btnImportPdf).setEnabled(enabled);
        findViewById(R.id.btnImportAudio).setEnabled(enabled);
        findViewById(R.id.btnRecordVoice).setEnabled(enabled);
        findViewById(R.id.btnAnalyzeYouTube).setEnabled(enabled);
        findViewById(R.id.btnAnalyzePasted).setEnabled(enabled);
    }

    private void startVoiceRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }
        if (recording) {
            stopRecording();
            return;
        }
        try {
            java.io.File dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
            if (dir == null) dir = getFilesDir();
            recordFile = new java.io.File(dir, "recording_" + System.currentTimeMillis() + ".m4a");
            MediaRecorder recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setOutputFile(recordFile.getAbsolutePath());
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.prepare();
            recorder.start();
            mediaRecorder = recorder;
            recording = true;
            View btn = findViewById(R.id.btnRecordVoice);
            if (btn instanceof com.google.android.material.button.MaterialButton) {
                ((com.google.android.material.button.MaterialButton) btn).setText("Stop recording");
            }
            Toast.makeText(this, "Recording... Tap again to stop", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Recording failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopRecording() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
            } catch (Exception ignored) {}
            mediaRecorder = null;
        }
        recording = false;
        View btn = findViewById(R.id.btnRecordVoice);
        if (btn instanceof com.google.android.material.button.MaterialButton) {
            ((com.google.android.material.button.MaterialButton) btn).setText("Record voice (Whisper)");
        }
        if (recordFile != null && recordFile.exists()) {
            processAudio(Uri.fromFile(recordFile), "Voice recording");  // same pipeline as Import Audio/Video → transcript mode
        }
        recordFile = null;
    }

    private MediaRecorder mediaRecorder;
    private java.io.File recordFile;
    private boolean recording;

    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (granted) startVoiceRecording();
                else Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show();
            }
    );
}
