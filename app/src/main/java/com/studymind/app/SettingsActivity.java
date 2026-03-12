package com.studymind.app;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.appbar.MaterialToolbar;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREF_THEME = "theme_dark";
    private static final String PREF_NAME = "studymind";
    private static final String PREF_YOUTUBE_REMEMBER_VISUAL = "youtube_remember_visual_choice";

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

        SwitchCompat themeSwitch = findViewById(R.id.themeSwitch);
        if (themeSwitch.getTextOn() == null) themeSwitch.setTextOn(" ");
        if (themeSwitch.getTextOff() == null) themeSwitch.setTextOff(" ");
        boolean darkDefault = getSharedPreferences(PREF_NAME, MODE_PRIVATE).getBoolean(PREF_THEME, true);
        themeSwitch.setChecked(darkDefault);
        themeSwitch.setOnCheckedChangeListener((v, isChecked) -> {
            getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit().putBoolean(PREF_THEME, isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        });

        findViewById(R.id.btnResetYoutubeChoice).setOnClickListener(v -> {
            getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit()
                    .remove(PREF_YOUTUBE_REMEMBER_VISUAL)
                    .remove("youtube_prefer_gemini")
                    .apply();
            Toast.makeText(this, "Cleared. You'll be asked again next time.", Toast.LENGTH_SHORT).show();
        });
    }
}
