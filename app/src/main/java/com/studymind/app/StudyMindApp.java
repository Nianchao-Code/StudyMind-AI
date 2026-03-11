package com.studymind.app;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.studymind.app.api.AIApiClient;
import com.studymind.app.api.MockAIApiClient;
import com.studymind.app.api.OpenAIApiClient;

/**
 * Application entry point, provides global AI client.
 */
public class StudyMindApp extends Application {
    private static AIApiClient aiApiClient;

    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        String apiKey = BuildConfig.OPENAI_API_KEY;
        aiApiClient = (apiKey != null && !apiKey.isEmpty())
                ? new OpenAIApiClient(apiKey)
                : new MockAIApiClient();  // Use mock when not configured, for testing
    }

    public static AIApiClient getAIApiClient() {
        return aiApiClient;
    }
}
