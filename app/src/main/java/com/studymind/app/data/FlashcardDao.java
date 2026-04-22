package com.studymind.app.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface FlashcardDao {
    @Insert
    long insert(Flashcard card);

    @Insert
    List<Long> insertAll(List<Flashcard> cards);

    @Update
    void update(Flashcard card);

    @Query("SELECT * FROM flashcards WHERE noteId = :noteId ORDER BY createdAt ASC")
    List<Flashcard> getByNoteId(long noteId);

    @Query("SELECT COUNT(*) FROM flashcards WHERE noteId = :noteId")
    int countByNoteId(long noteId);

    /** Cards the user has rated "got it" more times than "missed", and at least twice. */
    @Query("SELECT COUNT(*) FROM flashcards WHERE noteId = :noteId AND lastStudiedAt > 0 AND gotItCount > missedCount AND gotItCount >= 2")
    int countMasteredByNoteId(long noteId);

    @Query("DELETE FROM flashcards WHERE noteId = :noteId")
    void deleteByNoteId(long noteId);
}
