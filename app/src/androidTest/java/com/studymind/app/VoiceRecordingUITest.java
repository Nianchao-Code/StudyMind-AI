package com.studymind.app;

import android.Manifest;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;

import com.google.android.material.button.MaterialButton;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented tests for voice recording UI behavior.
 * Covers TC1 (happy path button toggle), TC2 (lifecycle), TC3 (permission UX).
 */
@RunWith(AndroidJUnit4.class)
public class VoiceRecordingUITest {

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule =
            new ActivityScenarioRule<>(MainActivity.class);

    @Rule
    public GrantPermissionRule permissionRule =
            GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO);

    /** TC1: Verify button text changes to "Stop recording" when recording starts. */
    @Test
    public void testRecordButtonTogglesText() {
        activityRule.getScenario().onActivity(activity -> {
            MaterialButton btn = activity.findViewById(R.id.btnRecordVoice);
            assertNotNull(btn);
            assertEquals("Record voice (Whisper)", btn.getText().toString());

            // Start recording
            btn.performClick();
            assertTrue(btn.getText().toString().startsWith("Stop recording"));
        });
    }

    /** TC1: Verify button text resets after stopping recording. */
    @Test
    public void testStopRecordingResetsButton() {
        activityRule.getScenario().onActivity(activity -> {
            MaterialButton btn = activity.findViewById(R.id.btnRecordVoice);

            // Start then stop
            btn.performClick();
            assertTrue(btn.getText().toString().startsWith("Stop recording"));

            btn.performClick();
            assertEquals("Record voice (Whisper)", btn.getText().toString());
        });
    }

    /** TC2: Verify recording stops gracefully when activity is stopped (simulates incoming call). */
    @Test
    public void testLifecycleInterruption_stopsRecordingGracefully() {
        activityRule.getScenario().onActivity(activity -> {
            MaterialButton btn = activity.findViewById(R.id.btnRecordVoice);
            btn.performClick(); // start recording
            assertTrue(btn.getText().toString().startsWith("Stop recording"));
        });

        // Simulate lifecycle interruption (like an incoming call)
        activityRule.getScenario().moveToState(androidx.lifecycle.Lifecycle.State.CREATED);

        // Return to foreground
        activityRule.getScenario().moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);

        activityRule.getScenario().onActivity(activity -> {
            MaterialButton btn = activity.findViewById(R.id.btnRecordVoice);
            // Button should be reset — recording was stopped by onStop()
            assertEquals("Record voice (Whisper)", btn.getText().toString());
        });
    }
}
