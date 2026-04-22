package com.studymind.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.studymind.app.data.StudyNote;
import com.studymind.app.rag.RagSearchService;

import java.util.List;

/**
 * Cross-note Q&A screen: user asks a free-form question; the service embeds it,
 * ranks saved notes by cosine similarity, and asks the chat model with context.
 */
public class AskAllActivity extends AppCompatActivity {

    private TextInputEditText questionInput;
    private MaterialButton btnAsk;
    private View progressRow;
    private TextView progressText;
    private MaterialCardView answerCard;
    private TextView answerText;
    private TextView sourcesLabel;
    private ChipGroup sourcesGroup;
    private RagSearchService ragService;

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
