package com.studymind.app;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.studymind.app.agent.FlashcardQuizParser;
import com.studymind.app.data.AppDatabase;
import com.studymind.app.data.Flashcard;
import com.studymind.app.data.FlashcardDao;
import com.studymind.app.flashcard.FlashcardPagerAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Interactive flashcard study loop backed by Room. Cards for the given note are
 * loaded and sorted by a tiny spaced-repetition heuristic (missed-heavy and
 * unseen cards first), rated as "got it" / "still learning", and persisted.
 */
public class FlashcardStudyActivity extends AppCompatActivity {

    public static final String EXTRA_NOTE_ID = "note_id";
    public static final String EXTRA_NOTE_TITLE = "note_title";
    /** Optional: retry only cards that are currently "shaky" (missed > got-it). */
    public static final String EXTRA_SHAKY_ONLY = "shaky_only";

    private ViewPager2 pager;
    private ProgressBar progressBar;
    private TextView progressLabel;
    private TextView scoreLabel;
    private MaterialButton btnGotIt;
    private MaterialButton btnStillLearning;
    private View actionRow;
    private MaterialCardView summaryCard;
    private TextView summaryScore;
    private TextView summaryDetail;
    private MaterialButton btnRetryMissed;
    private MaterialButton btnDone;

    private long noteId;
    private List<Flashcard> currentDeck = new ArrayList<>();
    private FlashcardPagerAdapter adapter;
    private int gotItThisSession = 0;
    private final Set<Integer> missedPositions = new HashSet<>();

    private FlashcardDao dao;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flashcard_study);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        String title = getIntent().getStringExtra(EXTRA_NOTE_TITLE);
        if (title != null && !title.isEmpty()) toolbar.setSubtitle(title);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        pager = findViewById(R.id.pager);
        progressBar = findViewById(R.id.progressBar);
        progressLabel = findViewById(R.id.progressLabel);
        scoreLabel = findViewById(R.id.scoreLabel);
        actionRow = findViewById(R.id.actionRow);
        btnGotIt = findViewById(R.id.btnGotIt);
        btnStillLearning = findViewById(R.id.btnStillLearning);
        summaryCard = findViewById(R.id.summaryCard);
        summaryScore = findViewById(R.id.summaryScore);
        summaryDetail = findViewById(R.id.summaryDetail);
        btnRetryMissed = findViewById(R.id.btnRetryMissed);
        btnDone = findViewById(R.id.btnDone);

        noteId = getIntent().getLongExtra(EXTRA_NOTE_ID, -1L);
        if (noteId < 0) {
            Toast.makeText(this, "Invalid note", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        dao = AppDatabase.getInstance(getApplicationContext()).flashcardDao();

        btnGotIt.setOnClickListener(v -> rate(true));
        btnStillLearning.setOnClickListener(v -> rate(false));
        btnDone.setOnClickListener(v -> finish());
        btnRetryMissed.setOnClickListener(v -> retryShaky());

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) { updateControlsForPosition(position); }
        });

        boolean shakyOnly = getIntent().getBooleanExtra(EXTRA_SHAKY_ONLY, false);
        loadDeck(shakyOnly);
    }

    private void loadDeck(boolean shakyOnly) {
        io.execute(() -> {
            List<Flashcard> cards = dao.getByNoteId(noteId);
            if (shakyOnly) {
                List<Flashcard> filtered = new ArrayList<>();
                for (Flashcard c : cards) {
                    if (c.missedCount > c.gotItCount || c.lastStudiedAt == 0) filtered.add(c);
                }
                cards = filtered;
            }
            // SRS ordering: higher priority first. Fresh cards (lastStudiedAt==0) at front.
            Collections.sort(cards, new Comparator<Flashcard>() {
                @Override public int compare(Flashcard a, Flashcard b) {
                    return Integer.compare(b.studyPriority(), a.studyPriority());
                }
            });
            final List<Flashcard> finalCards = cards;
            runOnUiThread(() -> startDeck(finalCards));
        });
    }

    private void startDeck(List<Flashcard> cards) {
        if (cards == null || cards.isEmpty()) {
            Toast.makeText(this, "No cards to study", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentDeck = cards;
        gotItThisSession = 0;
        missedPositions.clear();
        summaryCard.setVisibility(View.GONE);
        pager.setVisibility(View.VISIBLE);
        actionRow.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);
        progressLabel.setVisibility(View.VISIBLE);
        scoreLabel.setVisibility(View.VISIBLE);

        List<FlashcardQuizParser.Card> views = new ArrayList<>();
        for (Flashcard c : cards) views.add(new FlashcardQuizParser.Card(c.front, c.back));

        adapter = new FlashcardPagerAdapter(views, position ->
                updateControlsForPosition(pager.getCurrentItem()));
        pager.setAdapter(adapter);
        pager.setCurrentItem(0, false);
        updateControlsForPosition(0);
    }

    private void updateControlsForPosition(int position) {
        if (adapter == null) return;
        int total = adapter.getItemCount();
        progressLabel.setText("Card " + (position + 1) + " of " + total);
        progressBar.setProgress((int) ((position + 1) * 100f / total));
        scoreLabel.setText("Got it: " + gotItThisSession);
        boolean revealed = adapter.isRevealed(position);
        btnGotIt.setEnabled(revealed);
        btnStillLearning.setEnabled(revealed);
    }

    private void rate(boolean gotIt) {
        int position = pager.getCurrentItem();
        if (position < 0 || position >= currentDeck.size()) return;
        Flashcard card = currentDeck.get(position);
        if (gotIt) {
            gotItThisSession++;
            card.gotItCount++;
        } else {
            missedPositions.add(position);
            card.missedCount++;
        }
        card.lastStudiedAt = System.currentTimeMillis();
        io.execute(() -> dao.update(card));

        int total = adapter.getItemCount();
        if (position + 1 < total) {
            pager.setCurrentItem(position + 1, true);
        } else {
            showSummary();
        }
        scoreLabel.setText("Got it: " + gotItThisSession);
    }

    private void showSummary() {
        int total = adapter.getItemCount();
        pager.setVisibility(View.GONE);
        actionRow.setVisibility(View.GONE);
        progressBar.setProgress(100);
        summaryCard.setVisibility(View.VISIBLE);
        summaryScore.setText(gotItThisSession + " / " + total);
        int missed = missedPositions.size();
        if (missed == 0) {
            summaryDetail.setText("Perfect run! Progress saved.");
            btnRetryMissed.setEnabled(false);
            btnRetryMissed.setAlpha(0.5f);
        } else {
            summaryDetail.setText(missed == 1
                    ? "1 card to review again. Progress saved."
                    : missed + " cards to review again. Progress saved.");
            btnRetryMissed.setEnabled(true);
            btnRetryMissed.setAlpha(1f);
        }
    }

    private void retryShaky() {
        List<Flashcard> shaky = new ArrayList<>();
        for (int i : missedPositions) shaky.add(currentDeck.get(i));
        if (shaky.isEmpty()) {
            Toast.makeText(this, "Nothing to retry", Toast.LENGTH_SHORT).show();
            return;
        }
        startDeck(shaky);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }
}
