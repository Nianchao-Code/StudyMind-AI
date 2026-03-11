package com.studymind.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.FileProvider;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import androidx.core.content.ContextCompat;
import com.studymind.app.agent.FlashcardQuizParser;
import com.studymind.app.agent.NoteQAAgent;
import com.studymind.app.agent.StructuredNotes;
import com.studymind.app.data.NoteRepository;
import com.studymind.app.data.StudyNote;

public class NoteDetailActivity extends AppCompatActivity {

    private StudyNote note;
    private NoteRepository repository;
    private TextView noteContent;
    private LinearLayout noteSectionsContainer;
    private TextInputEditText questionInput;
    private TextView answerText;
    private View answerArea;
    private View answerLoading;
    private View answerScroll;
    private LinearLayout cardsContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_detail);

        long id = getIntent().getLongExtra("id", -1);
        if (id < 0) {
            finish();
            return;
        }

        noteContent = findViewById(R.id.noteContent);
        noteSectionsContainer = findViewById(R.id.noteSectionsContainer);
        questionInput = findViewById(R.id.questionInput);
        answerText = findViewById(R.id.answerText);
        answerArea = findViewById(R.id.answerArea);
        answerLoading = findViewById(R.id.answerLoading);
        answerScroll = findViewById(R.id.answerScroll);
        cardsContainer = findViewById(R.id.cardsContainer);
        View loadingNote = findViewById(R.id.loadingNote);
        View contentContainer = findViewById(R.id.noteContentContainer);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        repository = new NoteRepository(this);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        findViewById(R.id.btnAsk).setOnClickListener(v -> askQuestion());
        findViewById(R.id.chipExplain).setOnClickListener(v -> askQuestion("Explain the key concepts in simple terms."));
        findViewById(R.id.chipQuiz).setOnClickListener(v -> askQuestion("Give me 3 practice questions. For each use: Question 1: [question] Answer: [answer]. Then Question 2: ... etc."));
        findViewById(R.id.chipPitfalls).setOnClickListener(v -> askQuestion("What are the most common exam pitfalls for this material?"));
        findViewById(R.id.chipFlashcards).setOnClickListener(v -> askQuestion("Generate 5 flashcards. For each card, use exactly this format on a new line: *Front:* [question] *Back:* [answer]. No other formatting."));

        loadingNote.setVisibility(View.VISIBLE);
        contentContainer.setVisibility(View.GONE);
        loadNote(id);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_note_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        if (item.getItemId() == R.id.action_edit) {
            editTitle();
            return true;
        }
        if (item.getItemId() == R.id.action_edit_content) {
            editContent();
            return true;
        }
        if (item.getItemId() == R.id.action_tags) {
            editTags();
            return true;
        }
        if (item.getItemId() == R.id.action_pin) {
            togglePin();
            return true;
        }
        if (item.getItemId() == R.id.action_export_pdf) {
            exportPdf();
            return true;
        }
        if (item.getItemId() == R.id.action_copy) {
            copyNote();
            return true;
        }
        if (item.getItemId() == R.id.action_share) {
            shareNote();
            return true;
        }
        if (item.getItemId() == R.id.action_delete) {
            confirmDelete();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private String cleanSectionContent(String s) {
        if (s == null) return "";
        s = s.trim();
        s = s.replaceFirst("^(?i)N/A\\s*([-:]\\s*[^\\n]*)?[\\n\\r]*", "");
        s = s.replaceAll("(?m)^\\s*N/A\\s*([-:][^\\n]*)?\\s*[\\r\\n]*", "");
        return s.trim().replaceAll("\\n{3,}", "\n\n");
    }

    private boolean hasAnyContent(StructuredNotes n) {
        return (n.keyDefinitions != null && !n.keyDefinitions.isEmpty())
                || (n.coreConcepts != null && !n.coreConcepts.isEmpty())
                || (n.importantFormulas != null && !n.importantFormulas.isEmpty())
                || (n.commonPitfalls != null && !n.commonPitfalls.isEmpty())
                || (n.quickReview != null && !n.quickReview.isEmpty());
    }

    private void renderModularNote(StructuredNotes notes) {
        noteSectionsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        String[] titles = {"Key Definitions", "Core Concepts", "Formulas & Steps", "Common Pitfalls", "Quick Review"};
        String[] contents = {
                notes.keyDefinitions,
                notes.coreConcepts,
                notes.importantFormulas,
                notes.commonPitfalls,
                notes.quickReview
        };
        for (int i = 0; i < titles.length; i++) {
            String cleaned = cleanSectionContent(contents[i]);
            if (cleaned.isEmpty()) continue;
            View card = inflater.inflate(R.layout.item_note_section, noteSectionsContainer, false);
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
            noteSectionsContainer.addView(card);
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

    private void loadNote(long id) {
        repository.getById(id, n -> runOnUiThread(() -> {
                View loadingNote = findViewById(R.id.loadingNote);
                View contentContainer = findViewById(R.id.noteContentContainer);
                note = n;
                if (n == null) {
                    finish();
                    return;
                }
                loadingNote.setVisibility(View.GONE);
                contentContainer.setVisibility(View.VISIBLE);
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(note.title);
                }
                String content = note.content;
                StructuredNotes notes = StructuredNotes.fromJson(content);
                if (notes != null && hasAnyContent(notes)) {
                    renderModularNote(notes);
                    noteSectionsContainer.setVisibility(View.VISIBLE);
                    noteContent.setVisibility(View.GONE);
                } else {
                    noteContent.setText(content != null ? content : "");
                    noteSectionsContainer.setVisibility(View.GONE);
                    noteContent.setVisibility(View.VISIBLE);
                }
            }));
    }

    private void askQuestion() {
        String q = questionInput != null && questionInput.getText() != null
                ? questionInput.getText().toString().trim() : "";
        if (TextUtils.isEmpty(q)) {
            Toast.makeText(this, "Enter a question", Toast.LENGTH_SHORT).show();
            return;
        }
        askQuestion(q);
    }

    private void askQuestion(String question) {
        if (note == null) return;
        if (StudyMindApp.getAIApiClient() instanceof com.studymind.app.api.MockAIApiClient) {
            Toast.makeText(this, "Configure TRANSCRIPT_BACKEND_URL or OPENAI_API_KEY.", Toast.LENGTH_LONG).show();
            return;
        }

        setAnswerLoading(true);
        answerArea.setVisibility(View.VISIBLE);
        answerLoading.setVisibility(View.VISIBLE);
        answerScroll.setVisibility(View.GONE);
        answerText.setVisibility(View.GONE);
        cardsContainer.removeAllViews();

        String content = note.content != null ? note.content : "";
        StructuredNotes notes = StructuredNotes.fromJson(content);
        content = notes != null ? notes.toDisplayText() : content;

        NoteQAAgent qa = new NoteQAAgent(StudyMindApp.getAIApiClient());
        qa.ask(content, question, answer -> runOnUiThread(() -> showAnswer(answer, question)));
    }

    private void setAnswerLoading(boolean loading) {
        View chips = findViewById(R.id.quickQuestionChips);
        View btnAsk = findViewById(R.id.btnAsk);
        if (chips != null) chips.setEnabled(!loading);
        if (btnAsk != null) btnAsk.setEnabled(!loading);
    }

    private void showAnswer(String answer, String question) {
        setAnswerLoading(false);
        answerLoading.setVisibility(View.GONE);

        boolean isFlashcardPrompt = question.toLowerCase().contains("flashcard");
        boolean isQuizPrompt = question.toLowerCase().contains("question") || question.toLowerCase().contains("quiz");

        if (isFlashcardPrompt) {
            java.util.List<FlashcardQuizParser.Card> cards = FlashcardQuizParser.parseFlashcards(answer);
            if (cards != null && !cards.isEmpty()) {
                renderCards(cards);
                answerScroll.setVisibility(View.VISIBLE);
                answerText.setVisibility(View.GONE);
                return;
            }
        }
        if (isQuizPrompt) {
            java.util.List<FlashcardQuizParser.Card> cards = FlashcardQuizParser.parseQuiz(answer);
            if (cards != null && !cards.isEmpty()) {
                renderCards(cards);
                answerScroll.setVisibility(View.VISIBLE);
                answerText.setVisibility(View.GONE);
                return;
            }
        }
        // Fallback: plain text
        answerText.setText(answer != null ? answer : "");
        answerText.setVisibility(View.VISIBLE);
        answerScroll.setVisibility(View.GONE);
    }

    private void renderCards(java.util.List<FlashcardQuizParser.Card> cards) {
        cardsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (FlashcardQuizParser.Card card : cards) {
            View cardView = inflater.inflate(R.layout.item_flashcard, cardsContainer, false);
            TextView front = cardView.findViewById(R.id.cardFront);
            TextView back = cardView.findViewById(R.id.cardBack);
            TextView hint = cardView.findViewById(R.id.tapHint);
            front.setText(card.front);
            back.setText(card.back);
            back.setVisibility(View.GONE);
            cardView.setOnClickListener(v -> {
                boolean revealed = back.getVisibility() == View.VISIBLE;
                back.setVisibility(revealed ? View.GONE : View.VISIBLE);
                hint.setText(revealed ? "Tap to reveal answer" : "Tap to hide");
            });
            cardsContainer.addView(cardView);
        }
    }

    private void copyNote() {
        if (note == null) return;
        StructuredNotes notes = StructuredNotes.fromJson(note.content);
        String text = notes != null ? notes.toDisplayText() : note.content;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("StudyMind note", text));
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareNote() {
        if (note == null) return;
        StructuredNotes notes = StructuredNotes.fromJson(note.content);
        String text = notes != null ? notes.toDisplayText() : note.content;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, note.title);
        share.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(share, "Share note"));
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Delete note")
                .setMessage("Are you sure you want to delete this note?")
                .setPositiveButton("Delete", (d, w) -> deleteNote())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteNote() {
        if (note == null) return;
        repository.deleteById(note.id, () -> runOnUiThread(() -> {
            Toast.makeText(this, "Note deleted", Toast.LENGTH_SHORT).show();
            finish();
        }));
    }

    private void editContent() {
        if (note == null) return;
        StructuredNotes notes = StructuredNotes.fromJson(note.content);
        String currentText = notes != null ? notes.toDisplayText() : (note.content != null ? note.content : "");
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_note, null);
        TextInputEditText input = dialogView.findViewById(R.id.noteContentInput);
        input.setText(currentText);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Edit note")
                .setView(dialogView)
                .setPositiveButton("Save", (d, w) -> {
                    String newContent = input.getText() != null ? input.getText().toString() : "";
                    note.content = newContent;
                    repository.update(note, null);
                    StructuredNotes updated = StructuredNotes.fromJson(newContent);
                    if (updated != null && hasAnyContent(updated)) {
                        renderModularNote(updated);
                        noteSectionsContainer.setVisibility(View.VISIBLE);
                        noteContent.setVisibility(View.GONE);
                    } else {
                        noteContent.setText(newContent);
                        noteSectionsContainer.setVisibility(View.GONE);
                        noteContent.setVisibility(View.VISIBLE);
                    }
                    Toast.makeText(this, "Note updated", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void editTitle() {
        if (note == null) return;
        TextInputEditText input = new TextInputEditText(this);
        input.setPadding(48, 32, 48, 32);
        input.setText(note.title);
        input.setHint("Note title");
        new MaterialAlertDialogBuilder(this)
                .setTitle("Edit title")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String newTitle = input.getText() != null ? input.getText().toString().trim() : "";
                    if (!newTitle.isEmpty()) {
                        note.title = newTitle;
                        repository.update(note, null);
                        if (getSupportActionBar() != null) getSupportActionBar().setTitle(newTitle);
                        Toast.makeText(this, "Title updated", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void editTags() {
        if (note == null) return;
        TextInputEditText input = new TextInputEditText(this);
        input.setPadding(48, 32, 48, 32);
        input.setText(note.tags);
        input.setHint("e.g. Algorithms, Midterm");
        new MaterialAlertDialogBuilder(this)
                .setTitle("Edit tags")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    note.tags = input.getText() != null ? input.getText().toString().trim() : "";
                    repository.update(note, null);
                    Toast.makeText(this, "Tags updated", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void togglePin() {
        if (note == null) return;
        note.isPinned = !note.isPinned;
        repository.update(note, null);
        Toast.makeText(this, note.isPinned ? "Pinned" : "Unpinned", Toast.LENGTH_SHORT).show();
    }

    private void exportPdf() {
        if (note == null) return;
        StructuredNotes notes = StructuredNotes.fromJson(note.content);
        String text = notes != null ? notes.toDisplayText() : note.content;
        try {
            PdfDocument pdf = new PdfDocument();
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setTextSize(12);
            paint.setAntiAlias(true);
            int pageWidth = 595, pageHeight = 842;
            int margin = 40, lineHeight = 18, y = 50;
            String[] lines = text.split("\n");
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();
            PdfDocument.Page page = pdf.startPage(pageInfo);
            for (String line : lines) {
                if (y > pageHeight - 50) {
                    pdf.finishPage(page);
                    pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();
                    page = pdf.startPage(pageInfo);
                    y = 50;
                }
                page.getCanvas().drawText(line, margin, y, paint);
                y += lineHeight;
            }
            pdf.finishPage(page);
            java.io.File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (dir == null) dir = getFilesDir();
            java.io.File file = new java.io.File(dir, (note.title != null ? note.title : "note").replaceAll("[^a-zA-Z0-9_-]", "_") + ".pdf");
            pdf.writeTo(new java.io.FileOutputStream(file));
            pdf.close();
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/pdf");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Export PDF"));
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
