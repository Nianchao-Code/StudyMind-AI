package com.studymind.app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.studymind.app.data.AppDatabase;
import com.studymind.app.data.Flashcard;
import com.studymind.app.data.StudyNote;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Read-only dashboard screen that aggregates notes + flashcard stats so the user
 * can see how much they've actually studied (not just captured).
 */
public class DashboardActivity extends AppCompatActivity {

    private TextView statNotes;
    private TextView statCards;
    private TextView statMastered;
    private TextView statMasteredPercent;
    private TextView statStreak;
    private LinearLayout weekBars;
    private LinearLayout weekLabels;
    private TextView weekSummary;
    private LinearLayout topDecksContainer;
    private TextView emptyLabel;

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        statNotes = findViewById(R.id.statNotes);
        statCards = findViewById(R.id.statCards);
        statMastered = findViewById(R.id.statMastered);
        statMasteredPercent = findViewById(R.id.statMasteredPercent);
        statStreak = findViewById(R.id.statStreak);
        weekBars = findViewById(R.id.weekBars);
        weekLabels = findViewById(R.id.weekLabels);
        weekSummary = findViewById(R.id.weekSummary);
        topDecksContainer = findViewById(R.id.topDecksContainer);
        emptyLabel = findViewById(R.id.emptyLabel);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadStats();
    }

    private void loadStats() {
        io.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            List<StudyNote> notes = db.studyNoteDao().getAll();
            List<Flashcard> cards = db.flashcardDao().getAll();

            int totalNotes = notes != null ? notes.size() : 0;
            int totalCards = cards != null ? cards.size() : 0;
            int mastered = 0;
            // Map noteId -> {cards, mastered, recentStudy}
            Map<Long, int[]> perNote = new HashMap<>();
            // Day key (yyyymmdd) -> count of cards rated that day
            Map<Integer, Integer> perDay = new HashMap<>();

            if (cards != null) {
                for (Flashcard c : cards) {
                    if (c.isMastered()) mastered++;
                    int[] agg = perNote.get(c.noteId);
                    if (agg == null) {
                        agg = new int[]{0, 0};
                        perNote.put(c.noteId, agg);
                    }
                    agg[0]++;
                    if (c.isMastered()) agg[1]++;
                    if (c.lastStudiedAt > 0) {
                        int key = dayKey(c.lastStudiedAt);
                        perDay.merge(key, c.gotItCount + c.missedCount, Integer::sum);
                    }
                }
            }

            int[] last7 = new int[7];
            Calendar cal = Calendar.getInstance();
            cal.setTimeZone(TimeZone.getDefault());
            resetToStartOfDay(cal);
            for (int i = 6; i >= 0; i--) {
                int key = yyyymmdd(cal);
                last7[6 - i] = perDay.getOrDefault(key, 0);
                cal.add(Calendar.DAY_OF_YEAR, -1);
            }

            int streak = calculateStreak(perDay);

            List<DeckRow> topDecks = new java.util.ArrayList<>();
            if (notes != null) {
                for (StudyNote n : notes) {
                    int[] agg = perNote.get(n.id);
                    if (agg == null || agg[0] == 0) continue;
                    topDecks.add(new DeckRow(n.id, n.title, agg[0], agg[1]));
                }
                java.util.Collections.sort(topDecks, (a, b) -> {
                    int byMastered = Integer.compare(b.mastered, a.mastered);
                    if (byMastered != 0) return byMastered;
                    return Integer.compare(b.total, a.total);
                });
                if (topDecks.size() > 5) topDecks = topDecks.subList(0, 5);
            }

            final int fNotes = totalNotes;
            final int fCards = totalCards;
            final int fMastered = mastered;
            final int[] fLast7 = last7;
            final int fStreak = streak;
            final List<DeckRow> fTop = topDecks;

            runOnUiThread(() -> renderStats(fNotes, fCards, fMastered, fLast7, fStreak, fTop));
        });
    }

    private void renderStats(int notes, int cards, int mastered, int[] last7, int streak,
                             List<DeckRow> topDecks) {
        statNotes.setText(String.valueOf(notes));
        statCards.setText(String.valueOf(cards));
        statMastered.setText(String.valueOf(mastered));
        if (cards > 0) {
            int pct = (int) Math.round(100.0 * mastered / cards);
            statMasteredPercent.setText(pct + "% of deck");
        } else {
            statMasteredPercent.setText("—");
        }
        statStreak.setText(String.valueOf(streak));

        renderWeekBars(last7);
        renderTopDecks(topDecks);
    }

    private void renderWeekBars(int[] last7) {
        weekBars.removeAllViews();
        weekLabels.removeAllViews();
        int max = 1;
        int total = 0;
        for (int v : last7) { if (v > max) max = v; total += v; }

        String[] labels = weekLabels(last7.length);
        float density = getResources().getDisplayMetrics().density;
        int barCorner = (int) (6 * density);
        int horizontalPad = (int) (4 * density);

        int primary = getResources().getColor(R.color.leetcode_primary, getTheme());
        int bg = 0x22000000;
        // (bg fallback: a transparent slate so empty days still show the rail)

        for (int i = 0; i < last7.length; i++) {
            LinearLayout col = new LinearLayout(this);
            LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            colLp.setMargins(horizontalPad, 0, horizontalPad, 0);
            col.setLayoutParams(colLp);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.BOTTOM);

            float frac = Math.max(0.06f, (float) last7[i] / (float) max);
            View bar = new View(this);
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, frac);
            bar.setLayoutParams(barLp);
            GradientDrawable bg1 = new GradientDrawable();
            bg1.setCornerRadius(barCorner);
            bg1.setColor(last7[i] > 0 ? primary : bg);
            bar.setBackground(bg1);
            col.addView(bar);
            weekBars.addView(col);

            TextView label = new TextView(this);
            LinearLayout.LayoutParams labLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            label.setLayoutParams(labLp);
            label.setText(labels[i]);
            label.setTextSize(10f);
            label.setGravity(Gravity.CENTER_HORIZONTAL);
            label.setTextColor(getResources().getColor(R.color.leetcode_text_secondary, getTheme()));
            weekLabels.addView(label);
        }

        if (total == 0) {
            weekSummary.setText("No study sessions in the last 7 days.");
        } else {
            weekSummary.setText(total + (total == 1 ? " card" : " cards") + " rated this week");
        }
    }

    private void renderTopDecks(List<DeckRow> rows) {
        topDecksContainer.removeAllViews();
        if (rows == null || rows.isEmpty()) {
            emptyLabel.setVisibility(View.VISIBLE);
            return;
        }
        emptyLabel.setVisibility(View.GONE);
        float density = getResources().getDisplayMetrics().density;
        int marginBottom = (int) (8 * density);
        int pad = (int) (14 * density);

        for (DeckRow r : rows) {
            MaterialCardView card = new MaterialCardView(this);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardLp.setMargins(0, 0, 0, marginBottom);
            card.setLayoutParams(cardLp);
            card.setRadius(12 * density);
            card.setCardElevation(0f);
            card.setStrokeWidth((int) density);
            card.setStrokeColor(getResources().getColor(R.color.leetcode_border, getTheme()));
            card.setCardBackgroundColor(getResources().getColor(R.color.leetcode_card, getTheme()));
            card.setClickable(true);
            card.setFocusable(true);
            card.setForeground(selectableItemBackground());

            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.HORIZONTAL);
            inner.setGravity(Gravity.CENTER_VERTICAL);
            inner.setPadding(pad, pad, pad, pad);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            textCol.setLayoutParams(colLp);

            TextView title = new TextView(this);
            title.setText(r.title != null && !r.title.isEmpty() ? r.title : "Untitled");
            title.setTextSize(14f);
            title.setTextColor(getResources().getColor(R.color.leetcode_text, getTheme()));
            title.setMaxLines(1);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            textCol.addView(title);

            TextView sub = new TextView(this);
            int pct = r.total > 0 ? (int) Math.round(100.0 * r.mastered / r.total) : 0;
            sub.setText(r.mastered + " / " + r.total + " mastered · " + pct + "%");
            sub.setTextSize(12f);
            sub.setTextColor(getResources().getColor(R.color.leetcode_text_secondary, getTheme()));
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            subLp.topMargin = (int) (2 * density);
            sub.setLayoutParams(subLp);
            textCol.addView(sub);

            TextView chevron = new TextView(this);
            chevron.setText("›");
            chevron.setTextSize(22f);
            chevron.setTextColor(getResources().getColor(R.color.leetcode_text_secondary, getTheme()));
            LinearLayout.LayoutParams chLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            chLp.setMarginStart((int) (8 * density));
            chevron.setLayoutParams(chLp);

            inner.addView(textCol);
            inner.addView(chevron);
            card.addView(inner);

            final long noteId = r.noteId;
            card.setOnClickListener(v -> {
                Intent intent = new Intent(this, NoteDetailActivity.class);
                intent.putExtra("id", noteId);
                startActivity(intent);
            });

            topDecksContainer.addView(card);
        }
    }

    private android.graphics.drawable.Drawable selectableItemBackground() {
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        return getResources().getDrawable(tv.resourceId, getTheme());
    }

    /** Labels aligned with last7: index 0 = (count-1) days ago, index count-1 = today. */
    private String[] weekLabels(int count) {
        String[] labels = new String[count];
        String[] names = new String[]{"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        Calendar c = Calendar.getInstance();
        resetToStartOfDay(c);
        c.add(Calendar.DAY_OF_YEAR, -(count - 1));
        for (int i = 0; i < count; i++) {
            labels[i] = names[c.get(Calendar.DAY_OF_WEEK) - 1];
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        return labels;
    }

    private int calculateStreak(Map<Integer, Integer> perDay) {
        if (perDay == null || perDay.isEmpty()) return 0;
        int streak = 0;
        Calendar c = Calendar.getInstance();
        resetToStartOfDay(c);
        // If the user didn't study today, still count a streak that ended yesterday
        // by starting the scan at yesterday if today is empty.
        int todayKey = yyyymmdd(c);
        if (!perDay.containsKey(todayKey)) c.add(Calendar.DAY_OF_YEAR, -1);
        while (perDay.containsKey(yyyymmdd(c))) {
            streak++;
            c.add(Calendar.DAY_OF_YEAR, -1);
        }
        return streak;
    }

    private int dayKey(long timeMs) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(timeMs);
        resetToStartOfDay(c);
        return yyyymmdd(c);
    }

    private int yyyymmdd(Calendar c) {
        return c.get(Calendar.YEAR) * 10000
                + (c.get(Calendar.MONTH) + 1) * 100
                + c.get(Calendar.DAY_OF_MONTH);
    }

    private void resetToStartOfDay(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }

    private static class DeckRow {
        final long noteId;
        final String title;
        final int total;
        final int mastered;
        DeckRow(long noteId, String title, int total, int mastered) {
            this.noteId = noteId; this.title = title; this.total = total; this.mastered = mastered;
        }
    }
}
