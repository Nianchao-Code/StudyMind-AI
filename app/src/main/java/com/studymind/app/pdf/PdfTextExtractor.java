package com.studymind.app.pdf;

import android.net.Uri;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.InputStream;

/**
 * Extracts text from PDF files using PdfBox-Android.
 */
public class PdfTextExtractor {

    public static String extract(android.content.Context context, Uri pdfUri) {
        try {
            PDFBoxResourceLoader.init(context);
            InputStream is = context.getContentResolver().openInputStream(pdfUri);
            if (is == null) return "";
            PDDocument doc = PDDocument.load(is);
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            doc.close();
            is.close();
            return text != null ? text.trim() : "";
        } catch (Exception e) {
            return "";
        }
    }
}
