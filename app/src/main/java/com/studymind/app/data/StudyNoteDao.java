package com.studymind.app.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface StudyNoteDao {
    @Insert
    long insert(StudyNote note);

    @Delete
    void delete(StudyNote note);

    @Query("SELECT * FROM study_notes ORDER BY isPinned DESC, createdAt DESC")
    List<StudyNote> getAll();

    @Query("SELECT * FROM study_notes WHERE title LIKE '%' || :query || '%' OR subject LIKE '%' || :query || '%' OR COALESCE(tags,'') LIKE '%' || :query || '%' ORDER BY isPinned DESC, createdAt DESC")
    List<StudyNote> search(String query);

    @Query("SELECT * FROM study_notes WHERE id = :id")
    StudyNote getById(long id);

    @Query("DELETE FROM study_notes WHERE id = :id")
    void deleteById(long id);

    @androidx.room.Update
    void update(StudyNote note);
}
