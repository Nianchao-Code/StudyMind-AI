package com.studymind.app.flashcard;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.studymind.app.R;
import com.studymind.app.agent.FlashcardQuizParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** ViewPager2 adapter for the flashcard study loop. Tracks per-card flip state. */
public class FlashcardPagerAdapter extends RecyclerView.Adapter<FlashcardPagerAdapter.VH> {

    public interface OnFlipListener {
        void onFlipped(int position);
    }

    private final List<FlashcardQuizParser.Card> cards;
    private final Set<Integer> revealed = new HashSet<>();
    private final OnFlipListener flipListener;

    public FlashcardPagerAdapter(List<FlashcardQuizParser.Card> cards, OnFlipListener listener) {
        this.cards = new ArrayList<>(cards);
        this.flipListener = listener;
    }

    public boolean isRevealed(int position) {
        return revealed.contains(position);
    }

    public FlashcardQuizParser.Card getCard(int position) {
        return cards.get(position);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.page_flashcard, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        FlashcardQuizParser.Card card = cards.get(position);
        holder.front.setText(card.front);
        holder.back.setText(card.back);
        boolean flipped = revealed.contains(position);

        holder.back.setVisibility(flipped ? View.VISIBLE : View.GONE);
        holder.divider.setVisibility(flipped ? View.VISIBLE : View.GONE);
        holder.sideLabel.setText(flipped ? "ANSWER" : "QUESTION");
        holder.icon.setText(flipped ? "💡" : "❓");
        holder.hint.setText(flipped ? "Rate yourself using the buttons below" : "Tap card to reveal answer");

        if (flipped) {
            holder.back.setAlpha(0f);
            holder.back.animate().alpha(1f).setDuration(220).start();
            holder.divider.setAlpha(0f);
            holder.divider.animate().alpha(1f).setDuration(220).start();
        }

        View.OnClickListener flip = v -> {
            if (revealed.contains(position)) return;
            revealed.add(position);
            notifyItemChanged(position);
            if (flipListener != null) flipListener.onFlipped(position);
        };
        holder.clickable.setOnClickListener(flip);
    }

    @Override
    public int getItemCount() {
        return cards.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final View clickable;
        final TextView icon;
        final TextView front;
        final TextView back;
        final TextView sideLabel;
        final TextView hint;
        final View divider;

        VH(@NonNull View itemView) {
            super(itemView);
            clickable = itemView.findViewById(R.id.pageClickable);
            icon = itemView.findViewById(R.id.pageIcon);
            front = itemView.findViewById(R.id.pageFront);
            back = itemView.findViewById(R.id.pageBack);
            sideLabel = itemView.findViewById(R.id.pageSideLabel);
            hint = itemView.findViewById(R.id.pageHint);
            divider = itemView.findViewById(R.id.pageDivider);
        }
    }
}
