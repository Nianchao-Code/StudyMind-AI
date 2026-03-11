package com.studymind.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.studymind.app.data.StudyNote;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.Holder> {

    private List<StudyNote> notes;
    private final OnNoteClick listener;

    public NoteAdapter(List<StudyNote> notes, OnNoteClick listener) {
        this.notes = notes;
        this.listener = listener;
    }

    public void setNotes(List<StudyNote> notes) {
        this.notes = notes;
        notifyDataSetChanged();
    }

    public StudyNote getNoteAt(int position) {
        if (position < 0 || position >= notes.size()) return null;
        return notes.get(position);
    }

    public void removeAt(int position) {
        if (position >= 0 && position < notes.size()) {
            notes.remove(position);
            notifyItemRemoved(position);
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        if (notes == null || position >= notes.size()) return;
        StudyNote n = notes.get(position);
        holder.title.setText(n.title != null ? n.title : "Untitled");
        String sub = (n.subject != null ? n.subject : "") + " • " +
                new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(new Date(n.createdAt));
        if (n.tags != null && !n.tags.isEmpty()) sub += " • " + n.tags;
        holder.subtitle.setText(sub);
        holder.pinIcon.setVisibility(n.isPinned ? View.VISIBLE : View.GONE);
        holder.itemView.setOnClickListener(v -> listener.onClick(n));
    }

    @Override
    public int getItemCount() {
        return notes != null ? notes.size() : 0;
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView title, subtitle;
        ImageView pinIcon;

        Holder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            subtitle = itemView.findViewById(R.id.subtitle);
            pinIcon = itemView.findViewById(R.id.pinIcon);
        }
    }

    public interface OnNoteClick {
        void onClick(StudyNote note);
    }
}
