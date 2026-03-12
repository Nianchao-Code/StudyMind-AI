package com.studymind.app.whisper;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits long audio/video into chunks for Whisper API.
 * Uses MediaExtractor + MediaMuxer (no FFmpeg).
 */
public class AudioChunker {
    private static final long CHUNK_DURATION_US = 10 * 60 * 1_000_000L; // 10 min per chunk
    private static final long MAX_FILE_BYTES = 24 * 1024 * 1024; // 24 MB (Whisper limit 25MB)

    /**
     * Split audio/video into chunks (10 min each). Returns list of temp files (caller should delete).
     */
    public static List<File> split(Context context, android.net.Uri uri) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(context, uri, null);

        int audioTrackIndex = -1;
        MediaFormat format = null;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat f = extractor.getTrackFormat(i);
            String mime = f.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                audioTrackIndex = i;
                format = f;
                break;
            }
        }
        if (audioTrackIndex < 0 || format == null) {
            extractor.release();
            throw new IOException("No audio track found");
        }

        long durationUs = format.containsKey(MediaFormat.KEY_DURATION)
                ? format.getLong(MediaFormat.KEY_DURATION) : 0;
        if (durationUs <= 0) {
            extractor.release();
            throw new IOException("Could not get audio duration");
        }

        extractor.selectTrack(audioTrackIndex);
        List<File> chunks = new ArrayList<>();
        File cacheDir = context.getCacheDir();
        long startUs = 0;
        int chunkIdx = 0;

        while (startUs < durationUs) {
            long endUs = Math.min(startUs + CHUNK_DURATION_US, durationUs);
            File chunkFile = new File(cacheDir, "whisper_chunk_" + chunkIdx + ".m4a");
            writeChunk(extractor, audioTrackIndex, format, startUs, endUs, chunkFile);
            chunks.add(chunkFile);
            startUs = endUs;
            chunkIdx++;
        }

        extractor.release();
        return chunks;
    }

    private static void writeChunk(MediaExtractor extractor, int trackIndex, MediaFormat format,
                                   long startUs, long endUs, File outputFile) throws IOException {
        MediaMuxer muxer = new MediaMuxer(outputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        int muxerTrack = muxer.addTrack(format);
        muxer.start();

        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        int read;
        while ((read = extractor.readSampleData(buffer, 0)) >= 0) {
            long sampleTime = extractor.getSampleTime();
            if (sampleTime >= endUs) break;
            if (sampleTime < startUs) {
                extractor.advance();
                buffer.clear();
                continue;
            }

            info.set(0, read, sampleTime - startUs, extractor.getSampleFlags());
            buffer.position(0);
            buffer.limit(read);
            muxer.writeSampleData(muxerTrack, buffer, info);
            buffer.clear();
            extractor.advance();
        }

        muxer.stop();
        muxer.release();
    }

    /** Read file to byte array. For small chunks. */
    public static byte[] readFile(File f) throws IOException {
        try (FileInputStream is = new FileInputStream(f)) {
            byte[] buf = new byte[8192];
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            int n;
            while ((n = is.read(buf)) > 0) baos.write(buf, 0, n);
            return baos.toByteArray();
        }
    }

    /** Delete temp chunk files. */
    public static void deleteChunks(List<File> chunks) {
        for (File f : chunks) {
            try {
                if (f != null && f.exists()) f.delete();
            } catch (Exception ignored) {}
        }
    }
}
