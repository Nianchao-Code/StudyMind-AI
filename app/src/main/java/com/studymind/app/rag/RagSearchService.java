package com.studymind.app.rag;

import android.content.Context;

import com.studymind.app.api.AIApiClient;
import com.studymind.app.data.NoteRepository;
import com.studymind.app.data.StudyNote;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Cross-note RAG:
 *  1. Embed question.
 *  2. Rank cached note embeddings by cosine similarity.
 *  3. Build a context-grounded prompt and ask the chat model.
 *
 * Missing / stale embeddings are lazily backfilled in batches and persisted.
 */
public class RagSearchService {

    private static final int TOP_K = 3;
    private static final int BATCH_SIZE = 16;
    private static final int PER_NOTE_CHAR_LIMIT = 2500;
    private static final String SYSTEM_PROMPT =
            "You are StudyMind AI, answering a student's question using only the supplied note excerpts. "
            + "Cite notes inline as [Note 1], [Note 2], etc. If the answer is not in the notes, say so plainly. "
            + "Be concise: 2-5 short paragraphs, plain text, no markdown headers.";

    private final Context appContext;
    private final NoteRepository repository;
    private final EmbeddingApiClient embedder;
    private final AIApiClient chatClient;

    public RagSearchService(Context context, String backendUrl, AIApiClient chatClient) {
        this.appContext = context.getApplicationContext();
        this.repository = new NoteRepository(this.appContext);
        this.embedder = new EmbeddingApiClient(backendUrl);
        this.chatClient = chatClient;
    }

    public boolean isConfigured() {
        return embedder.isConfigured() && chatClient != null;
    }

    public void ask(String question, AskCallback callback) {
        if (question == null || question.trim().isEmpty()) {
            callback.onError(new IllegalArgumentException("Question is empty"));
            return;
        }
        if (!isConfigured()) {
            callback.onError(new IllegalStateException("Backend not configured"));
            return;
        }

        repository.getAll(notes -> {
            if (notes == null || notes.isEmpty()) {
                callback.onError(new IllegalStateException("No saved notes yet. Save a note first."));
                return;
            }

            ensureEmbeddings(notes, () ->
                    embedder.embed(Collections.singletonList(question), new EmbeddingApiClient.Callback() {
                        @Override
                        public void onSuccess(float[][] embeddings) {
                            if (embeddings.length == 0) {
                                callback.onError(new IllegalStateException("Empty embedding returned"));
                                return;
                            }
                            List<Ranked> topK = rank(notes, embeddings[0]);
                            if (topK.isEmpty()) {
                                callback.onError(new IllegalStateException("No notes ready for search yet"));
                                return;
                            }
                            callAnswer(question, topK, callback);
                        }

                        @Override
                        public void onError(Throwable t) { callback.onError(t); }
                    })
            );
        });
    }

    private void ensureEmbeddings(List<StudyNote> notes, Runnable done) {
        List<StudyNote> missing = new ArrayList<>();
        for (StudyNote n : notes) {
            if (n.embedding == null || n.embedding.length < 4) missing.add(n);
        }
        if (missing.isEmpty()) { done.run(); return; }

        embedBatch(missing, 0, done);
    }

    private void embedBatch(List<StudyNote> missing, int start, Runnable done) {
        if (start >= missing.size()) { done.run(); return; }
        int end = Math.min(start + BATCH_SIZE, missing.size());
        List<StudyNote> slice = missing.subList(start, end);
        List<String> texts = new ArrayList<>(slice.size());
        for (StudyNote n : slice) texts.add(noteToEmbedText(n));

        embedder.embed(texts, new EmbeddingApiClient.Callback() {
            @Override
            public void onSuccess(float[][] vecs) {
                for (int i = 0; i < slice.size() && i < vecs.length; i++) {
                    StudyNote n = slice.get(i);
                    n.embedding = EmbeddingUtils.toBytes(vecs[i]);
                    repository.update(n, null);
                }
                embedBatch(missing, end, done);
            }

            @Override
            public void onError(Throwable t) {
                // Best-effort: skip failed batch, keep going so we can still rank the rest.
                embedBatch(missing, end, done);
            }
        });
    }

    private List<Ranked> rank(List<StudyNote> notes, float[] queryVec) {
        List<Ranked> scored = new ArrayList<>();
        for (StudyNote n : notes) {
            float[] nv = EmbeddingUtils.toFloats(n.embedding);
            if (nv == null) continue;
            float s = EmbeddingUtils.cosine(queryVec, nv);
            scored.add(new Ranked(n, s));
        }
        Collections.sort(scored, new Comparator<Ranked>() {
            @Override
            public int compare(Ranked a, Ranked b) { return Float.compare(b.score, a.score); }
        });
        if (scored.size() > TOP_K) return scored.subList(0, TOP_K);
        return scored;
    }

    private void callAnswer(String question, List<Ranked> topK, AskCallback callback) {
        StringBuilder ctx = new StringBuilder();
        for (int i = 0; i < topK.size(); i++) {
            StudyNote n = topK.get(i).note;
            ctx.append("[Note ").append(i + 1).append("] ").append(safe(n.title)).append('\n');
            if (n.subject != null && !n.subject.isEmpty()) {
                ctx.append("Subject: ").append(n.subject).append('\n');
            }
            ctx.append(truncate(noteToContextText(n), PER_NOTE_CHAR_LIMIT)).append("\n\n");
        }

        String prompt = "Use the notes below to answer the question.\n\n"
                + ctx + "Question: " + question;

        chatClient.chat(prompt, SYSTEM_PROMPT, new AIApiClient.ChatCallback() {
            @Override
            public void onSuccess(String response) {
                List<StudyNote> sources = new ArrayList<>();
                for (Ranked r : topK) sources.add(r.note);
                callback.onAnswer(response != null ? response.trim() : "", sources);
            }

            @Override
            public void onError(Throwable t) { callback.onError(t); }
        });
    }

    private static String noteToEmbedText(StudyNote n) {
        StringBuilder sb = new StringBuilder();
        if (n.title != null) sb.append(n.title).append('\n');
        if (n.subject != null) sb.append(n.subject).append('\n');
        if (n.tags != null) sb.append(n.tags).append('\n');
        String body = noteToContextText(n);
        sb.append(truncate(body, 4000));
        return sb.toString();
    }

    /** Expand the stored notes JSON into a flat, human-readable block for prompting / embedding. */
    private static String noteToContextText(StudyNote n) {
        if (n.content == null) return "";
        String c = n.content.trim();
        if (c.startsWith("{")) {
            try {
                com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(c).getAsJsonObject();
                StringBuilder sb = new StringBuilder();
                String[] keys = {"keyDefinitions", "coreConcepts", "importantFormulas", "commonPitfalls", "quickReview"};
                String[] labels = {"Key definitions", "Core concepts", "Formulas", "Pitfalls", "Quick review"};
                for (int i = 0; i < keys.length; i++) {
                    if (obj.has(keys[i]) && !obj.get(keys[i]).isJsonNull()) {
                        String v = obj.get(keys[i]).getAsString();
                        if (v != null && !v.trim().isEmpty()) {
                            sb.append(labels[i]).append(":\n").append(v.trim()).append("\n\n");
                        }
                    }
                }
                if (sb.length() > 0) return sb.toString().trim();
            } catch (Exception ignored) {}
        }
        return c;
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static class Ranked {
        final StudyNote note;
        final float score;
        Ranked(StudyNote n, float s) { this.note = n; this.score = s; }
    }

    public interface AskCallback {
        void onAnswer(String answer, List<StudyNote> sources);
        void onError(Throwable t);
    }
}
