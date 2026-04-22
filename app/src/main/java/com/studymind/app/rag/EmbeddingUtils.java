package com.studymind.app.rag;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Serialization + cosine similarity helpers for on-device RAG. */
public final class EmbeddingUtils {
    private EmbeddingUtils() {}

    public static byte[] toBytes(float[] vec) {
        if (vec == null) return null;
        ByteBuffer bb = ByteBuffer.allocate(vec.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : vec) bb.putFloat(f);
        return bb.array();
    }

    public static float[] toFloats(byte[] bytes) {
        if (bytes == null || bytes.length < 4) return null;
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] arr = new float[bytes.length / 4];
        for (int i = 0; i < arr.length; i++) arr[i] = bb.getFloat();
        return arr;
    }

    /** Cosine similarity in [-1, 1]; 0 when either vector is zero-length or mismatched. */
    public static float cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) return 0f;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0f;
        return (float) (dot / (Math.sqrt(na) * Math.sqrt(nb)));
    }
}
