package com.studymind.app.agent;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory LRU cache for content analysis results by content hash.
 */
public class AnalysisCache {
    private static final int MAX_ENTRIES = 50;

    private final Map<String, ContentAnalysisResult> cache = new LinkedHashMap<String, ContentAnalysisResult>(MAX_ENTRIES + 1, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ContentAnalysisResult> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    public String hashContent(String content) {
        if (content == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(content.hashCode());
        }
    }

    public ContentAnalysisResult get(String hash) {
        return cache.get(hash);
    }

    public void put(String hash, ContentAnalysisResult result) {
        cache.put(hash, result);
    }
}
