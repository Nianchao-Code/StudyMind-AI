package com.studymind.app.ocr;

import android.content.Context;
import android.net.Uri;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;

/**
 * On-device OCR using Google ML Kit Text Recognition (Latin script, bundled model).
 * Runs fully offline; no API key required.
 */
public class OcrHelper {

    public interface OcrCallback {
        void onSuccess(String text);
        void onError(Throwable t);
    }

    /** Recognize text from a local image URI (camera output or gallery pick). */
    public static void recognize(Context context, Uri imageUri, OcrCallback callback) {
        if (context == null || imageUri == null) {
            callback.onError(new IllegalArgumentException("Image URI is null"));
            return;
        }
        InputImage image;
        try {
            image = InputImage.fromFilePath(context, imageUri);
        } catch (IOException e) {
            callback.onError(e);
            return;
        }
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        recognizer.process(image)
                .addOnSuccessListener(result -> {
                    String text = collectText(result);
                    if (text == null || text.trim().isEmpty()) {
                        callback.onError(new IllegalStateException("No text detected in image"));
                    } else {
                        callback.onSuccess(text);
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    /** Preserve reading order by iterating blocks → lines, joining lines with newlines. */
    private static String collectText(Text result) {
        if (result == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Text.TextBlock block : result.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                sb.append(line.getText()).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }
}
