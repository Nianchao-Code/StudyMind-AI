package com.studymind.app;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        TextView apiStatus = findViewById(R.id.apiStatus);
        String openaiKey = BuildConfig.OPENAI_API_KEY;
        String youtubeKey = BuildConfig.YOUTUBE_API_KEY;
        apiStatus.setText("OpenAI: " + (openaiKey != null && !openaiKey.isEmpty() ? "Configured" : "Not set") + "\n"
                + "YouTube: " + (youtubeKey != null && !youtubeKey.isEmpty() ? "Configured" : "Not set (optional)"));
    }
}
