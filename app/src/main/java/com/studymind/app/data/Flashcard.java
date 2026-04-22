package com.studymind.app.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * A persistent flashcard belonging to a {@link StudyNote}. The pair of
 * ({@link #gotItCount}, {@link #missedCount}) drives the simple spaced-repetition
 * ordering: cards where missedCount - gotItCount is larger surface first.
 * Fresh cards ({@link #lastStudiedAt} == 0) always surface before any seen card.
 */
@Entity(
        tableName = "flashcards",
        foreignKeys = @ForeignKey(
                entity = StudyNote.class,
                parentColumns = "id",
                childColumns = "noteId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("noteId")}
)
public class Flashcard {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long noteId;

    public String front;
    public String back;

    @ColumnInfo(defaultValue = "0")
    public int gotItCount;

    @ColumnInfo(defaultValue = "0")
    public int missedCount;

    @ColumnInfo(defaultValue = "0")
    public long lastStudiedAt;

    public long createdAt;

    public Flashcard() {}

    @Ignore
    public Flashcard(long noteId, String front, String back) {
        this.noteId = noteId;
        this.front = front;
        this.back = back;
        this.createdAt = System.currentTimeMillis();
    }

    /** Higher == more "shaky". Fresh cards (never studied) get highest priority. */
    public int studyPriority() {
        if (lastStudiedAt == 0) return Integer.MAX_VALUE;
        return missedCount - gotItCount;
    }

    /** True if user has rated "got it" at least as often as "missed" and studied at least once. */
    public boolean isMastered() {
        return lastStudiedAt > 0 && gotItCount > missedCount && gotItCount >= 2;
    }
}
