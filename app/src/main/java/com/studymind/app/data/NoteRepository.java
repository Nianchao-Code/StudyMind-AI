package com.studymind.app.data;

import android.content.Context;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Repository for study notes. Offloads DB access to background executor.
 */
public class NoteRepository {
    private final StudyNoteDao dao;
    private final Executor executor = Executors.newSingleThreadExecutor();

    public NoteRepository(Context context) {
        dao = AppDatabase.getInstance(context).studyNoteDao();
    }

    public void getAll(LoadCallback<List<StudyNote>> callback) {
        executor.execute(() -> {
            try {
                callback.onResult(dao.getAll());
            } catch (Exception e) {
                callback.onResult(new java.util.ArrayList<>());
            }
        });
    }

    public void search(String query, LoadCallback<List<StudyNote>> callback) {
        executor.execute(() -> {
            try {
                callback.onResult(dao.search(query));
            } catch (Exception e) {
                callback.onResult(new java.util.ArrayList<>());
            }
        });
    }

    public void update(StudyNote note, LoadCallback<Void> callback) {
        executor.execute(() -> {
            dao.update(note);
            if (callback != null) callback.onResult(null);
        });
    }

    public void getById(long id, LoadCallback<StudyNote> callback) {
        executor.execute(() -> {
            StudyNote note = dao.getById(id);
            callback.onResult(note);
        });
    }

    public void insert(StudyNote note, LoadCallback<Long> callback) {
        executor.execute(() -> {
            long id = dao.insert(note);
            callback.onResult(id);
        });
    }

    public void deleteById(long id, Runnable onDone) {
        executor.execute(() -> {
            dao.deleteById(id);
            if (onDone != null) onDone.run();
        });
    }

    public interface LoadCallback<T> {
        void onResult(T result);
    }
}
