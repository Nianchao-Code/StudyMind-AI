package com.studymind.app.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses AI responses into flashcard or quiz Q&A pairs.
 */
public class FlashcardQuizParser {

    public static class Card {
        public final String front;
        public final String back;

        public Card(String front, String back) {
            this.front = front != null ? front.trim() : "";
            this.back = back != null ? back.trim() : "";
        }
    }

    /** Parse flashcards from AI response with *Front:* ... *Back:* format */
    public static List<Card> parseFlashcards(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        List<Card> cards = new ArrayList<>();
        // Match *Front:* content *Back:* content - content can have newlines
        Pattern p = Pattern.compile("(?i)\\*?Front:?\\*?\\s*(.+?)\\s*\\*?Back:?\\*?\\s*(.+?)(?=\\*?Front:|\\*\\*Flashcard|$)", Pattern.DOTALL);
        Matcher m = p.matcher(text);
        while (m.find()) {
            String f = clean(m.group(1));
            String b = clean(m.group(2));
            if (!f.isEmpty() && !b.isEmpty()) cards.add(new Card(f, b));
        }
        return cards.isEmpty() ? null : cards;
    }

    /** Parse quiz from AI response with Question/Answer format */
    public static List<Card> parseQuiz(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        List<Card> cards = new ArrayList<>();
        // Match Question N: ... Answer: ... or Q: ... A: ...
        Pattern p = Pattern.compile("(?:\\d+\\.|Question\\s*\\d*:?|Q:?)\\s*([^\\n]+?)\\s*(?i)(?:Answer:?|A:?)\\s*([^\\n]+?)(?=\\d+\\.|Question|Q:|$)", Pattern.DOTALL);
        Matcher m = p.matcher(text);
        while (m.find()) {
            String q = clean(m.group(1));
            String a = m.groupCount() >= 2 && m.group(2) != null ? clean(m.group(2)) : "";
            if (!q.isEmpty()) cards.add(new Card(q, a));
        }
        if (cards.isEmpty()) {
            // Fallback: split by "Answer:" or "A:"
            String[] qa = text.split("(?i)(?=\\s*Answer:|\\s*A:)");
            for (int i = 0; i < qa.length; i++) {
                String block = qa[i].trim();
                if (block.isEmpty()) continue;
                int sep = block.toLowerCase().indexOf("answer:");
                if (sep < 0) sep = block.toLowerCase().indexOf("a:");
                if (sep >= 0) {
                    String q = clean(block.substring(0, sep).replaceFirst("^[\\d.\\s*Question:]+", ""));
                    String a = clean(block.substring(sep).replaceFirst("(?i)(?:answer|a):\\s*", ""));
                    if (!q.isEmpty()) cards.add(new Card(q, a));
                } else if (!block.isEmpty()) {
                    cards.add(new Card(clean(block.replaceFirst("^[\\d.\\s*Question:]+", "")), ""));
                }
            }
        }
        return cards.isEmpty() ? null : cards;
    }

    private static String clean(String s) {
        if (s == null) return "";
        return s.replaceAll("\\*+", "").replaceAll("\\s+", " ").trim();
    }

    public static boolean looksLikeFlashcards(String text) {
        return text != null && text.toLowerCase().contains("front") && text.toLowerCase().contains("back");
    }

    public static boolean looksLikeQuiz(String text) {
        return text != null && (text.toLowerCase().contains("question") || text.matches("(?s).*\\d+\\.\\s+.*"));
    }
}
