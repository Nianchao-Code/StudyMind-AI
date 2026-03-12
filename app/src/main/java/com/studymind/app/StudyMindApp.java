package com.studymind.app;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.studymind.app.api.AIApiClient;
import com.studymind.app.api.BackendAIApiClient;
import com.studymind.app.api.MockAIApiClient;
import com.studymind.app.api.OpenAIApiClient;

/**
 * Application entry point, provides global AI client.
 * Prefers backend proxy (no keys in APK); falls back to direct OpenAI for dev.
 */
public class StudyMindApp extends Application {
    private static AIApiClient aiApiClient;

    @Override
    public void onCreate() {
        super.onCreate();
        boolean dark = getSharedPreferences("studymind", MODE_PRIVATE).getBoolean("theme_dark", true);
        AppCompatDelegate.setDefaultNightMode(dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        String backendUrl = BuildConfig.TRANSCRIPT_BACKEND_URL;
        String openaiKey = BuildConfig.OPENAI_API_KEY;

        if (backendUrl != null && !backendUrl.trim().isEmpty()) {
            aiApiClient = new BackendAIApiClient(backendUrl);
        } else if (openaiKey != null && !openaiKey.isEmpty()) {
            aiApiClient = new OpenAIApiClient(openaiKey);
        } else {
            aiApiClient = new MockAIApiClient();
        }
    }

    public static AIApiClient getAIApiClient() {
        return aiApiClient;
    }
}
