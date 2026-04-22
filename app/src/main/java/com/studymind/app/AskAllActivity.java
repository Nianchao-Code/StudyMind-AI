package com.studymind.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.studymind.app.data.StudyNote;
import com.studymind.app.rag.RagSearchService;
import com.studymind.app.whisper.WhisperApiClient;

import java.io.File;
import java.util.List;

/**
 * Cross-note Q&A screen: user asks a free-form question (typed or spoken);
 * the service embeds it, ranks saved notes by cosine similarity, and asks
 * the chat model with context.
 */
public class AskAllActivity extends AppCompatActivity {

    private TextInputEditText questionInput;
    private MaterialButton btnAsk;
    private MaterialButton btnMic;
    private View progressRow;
    private TextView progressText;
    private MaterialCardView answerCard;
    private TextView answerText;
    private TextView sourcesLabel;
    private ChipGroup sourcesGroup;
    private RagSearchService ragService;

    private MediaRecorder mediaRecorder;
    private File recordFile;
    private boolean recording = false;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String> micPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startRecording();
                else Toast.makeText(this, "Microphone permission denied",
                        Toast.LENGTH_SHORT).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ask_all);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        questionInput = findViewById(R.id.questionInput);
        btnAsk = findViewById(R.id.btnAsk);
        btnMic = findViewById(R.id.btnMic);
        progressRow = findViewById(R.id.progressRow);
        progressText = findViewById(R.id.progressText);
        answerCard = findViewById(R.id.answerCard);
        answerText = findViewById(R.id.answerText);
        sourcesLabel = findViewById(R.id.sourcesLabel);
        sourcesGroup = findViewById(R.id.sourcesGroup);

        String backendUrl = BuildConfig.TRANSCRIPT_BACKEND_URL;
        ragService = new RagSearchService(this, backendUrl, StudyMindApp.getAIApiClient());

        if (!ragService.isConfigured()) {
            Toast.makeText(this,
                    "Backend URL missing — set TRANSCRIPT_BACKEND_URL in local.properties",
                    Toast.LENGTH_LONG).show();
        }

        btnAsk.setOnClickListener(v -> ask());
        btnMic.setOnClickListener(v -> toggleMic());
    }

    private void toggleMic() {
        if (recording) {
            stopRecordingAndTranscribe();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }
        startRecording();
    }

    private void startRecording() {
        try {
            File dir = getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC);
            if (dir == null) dir = getFilesDir();
            recordFile = new File(dir, "voiceask_" + System.currentTimeMillis() + ".m4a");

            MediaRecorder recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setOutputFile(recordFile.getAbsolutePath());
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.prepare();
            recorder.start();

            mediaRecorder = recorder;
            recording = true;
            btnMic.setText("Stop");
            btnMic.setIcon(null);
            Toast.makeText(this, "Recording… tap again to stop", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't start mic: " + e.getMessage(), Toast.LENGTH_LONG).show();
            cleanupRecorder();
        }
    }

    private void stopRecordingAndTranscribe() {
        recording = false;
        cleanupRecorder();
        btnMic.setText("");
        btnMic.setIcon(getDrawable(android.R.drawable.ic_btn_speak_now));

        if (recordFile == null || !recordFile.exists() || recordFile.length() == 0) {
            Toast.makeText(this, "Nothing recorded", Toast.LENGTH_SHORT).show();
            return;
        }
        setLoading(true, "Transcribing…");
        btnMic.setEnabled(false);

        String apiKey = BuildConfig.OPENAI_API_KEY;
        String backendUrl = BuildConfig.TRANSCRIPT_BACKEND_URL;
        WhisperApiClient whisper = new WhisperApiClient(apiKey != null ? apiKey : "", backendUrl);
        Uri uri = Uri.fromFile(recordFile);
        whisper.transcribe(this, uri, recordFile.getName(), new WhisperApiClient.TranscribeCallback() {
            @Override
            public void onSuccess(String transcript) {
                uiHandler.post(() -> {
                    if (isFinishing()) return;
                    btnMic.setEnabled(true);
                    setLoading(false, null);
                    String text = transcript != null ? transcript.trim() : "";
                    if (text.isEmpty()) {
                        Toast.makeText(AskAllActivity.this, "Didn't catch that — try again.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    questionInput.setText(text);
                    questionInput.setSelection(text.length());
                    ask();
                });
            }
            @Override
            public void onError(Throwable t) {
                uiHandler.post(() -> {
                    if (isFinishing()) return;
                    btnMic.setEnabled(true);
                    setLoading(false, null);
                    String msg = t.getMessage() != null ? t.getMessage() : "Transcription failed";
                    Toast.makeText(AskAllActivity.this, msg, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void cleanupRecorder() {
        if (mediaRecorder != null) {
            try { mediaRecorder.stop(); } catch (Exception ignored) {}
            try { mediaRecorder.release(); } catch (Exception ignored) {}
            mediaRecorder = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (recording) {
            recording = false;
            cleanupRecorder();
            btnMic.setText("");
            btnMic.setIcon(getDrawable(android.R.drawable.ic_btn_speak_now));
        }
    }

    private void ask() {
        String q = questionInput.getText() != null ? questionInput.getText().toString().trim() : "";
        if (q.isEmpty()) {
            Toast.makeText(this, "Type a question first", Toast.LENGTH_SHORT).show();
            return;
        }
        hideKeyboard();
        setLoading(true, "Searching your notes…");
        answerCard.setVisibility(View.GONE);
        sourcesLabel.setVisibility(View.GONE);
        sourcesGroup.removeAllViews();

        ragService.ask(q, new RagSearchService.AskCallback() {
            @Override
            public void onAnswer(String answer, List<StudyNote> sources) {
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    setLoading(false, null);
                    answerText.setText(answer.isEmpty() ? "(empty response)" : answer);
                    answerCard.setVisibility(View.VISIBLE);
                    renderSources(sources);
                });
            }

            @Override
            public void onError(Throwable t) {
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    setLoading(false, null);
                    String msg = t.getMessage() != null ? t.getMessage() : "Unknown error";
                    Toast.makeText(AskAllActivity.this, msg, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void renderSources(List<StudyNote> sources) {
        sourcesGroup.removeAllViews();
        if (sources == null || sources.isEmpty()) {
            sourcesLabel.setVisibility(View.GONE);
            return;
        }
        sourcesLabel.setVisibility(View.VISIBLE);
        for (int i = 0; i < sources.size(); i++) {
            StudyNote n = sources.get(i);
            Chip chip = new Chip(this);
            String title = n.title != null && !n.title.isEmpty() ? n.title : ("Note " + (i + 1));
            chip.setText("[Note " + (i + 1) + "] " + title);
            chip.setClickable(true);
            chip.setCheckable(false);
            chip.setOnClickListener(v -> {
                Intent intent = new Intent(this, NoteDetailActivity.class);
                intent.putExtra("id", n.id);
                startActivity(intent);
            });
            sourcesGroup.addView(chip);
        }
    }

    private void setLoading(boolean loading, String message) {
        progressRow.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading && message != null) progressText.setText(message);
        btnAsk.setEnabled(!loading);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        View focus = getCurrentFocus();
        if (imm != null && focus != null) {
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }
}
