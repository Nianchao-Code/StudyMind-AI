package com.studymind.app.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {StudyNote.class, Flashcard.class}, version = 4, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE study_notes ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE study_notes ADD COLUMN tags TEXT");
        }
    };

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE study_notes ADD COLUMN embedding BLOB");
        }
    };

    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS flashcards (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "noteId INTEGER NOT NULL, " +
                            "front TEXT, " +
                            "back TEXT, " +
                            "gotItCount INTEGER NOT NULL DEFAULT 0, " +
                            "missedCount INTEGER NOT NULL DEFAULT 0, " +
                            "lastStudiedAt INTEGER NOT NULL DEFAULT 0, " +
                            "createdAt INTEGER NOT NULL, " +
                            "FOREIGN KEY(noteId) REFERENCES study_notes(id) ON DELETE CASCADE" +
                            ")"
            );
            db.execSQL("CREATE INDEX IF NOT EXISTS index_flashcards_noteId ON flashcards(noteId)");
        }
    };

    public abstract StudyNoteDao studyNoteDao();
    public abstract FlashcardDao flashcardDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "studymind_db")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
