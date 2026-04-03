package com.studymind.app.whisper;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Unit tests for WhisperApiClient covering:
 * - TC4: Network failure returns error (not hang)
 * - TC4: HTTP error codes surface properly
 * - Successful transcription parses JSON correctly
 */
public class WhisperApiClientTest {

    private MockWebServer server;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    public void testSuccessfulTranscription_parsesText() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"text\": \"Hello world this is a test\"}")
                .setResponseCode(200));

        WhisperApiClient client = new WhisperApiClient("test-key", null);
        String result = client.doTranscribeBlocking(
                new byte[]{1, 2, 3}, "test.m4a", "audio/mp4",
                server.url("/v1/audio/transcriptions").toString());

        assertEquals("Hello world this is a test", result);
    }

    @Test
    public void testHttpError_returnsEmptyOrThrows() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"error\": \"invalid_api_key\"}")
                .setResponseCode(401));

        WhisperApiClient client = new WhisperApiClient("bad-key", null);
        try {
            client.doTranscribeBlocking(
                    new byte[]{1, 2, 3}, "test.m4a", "audio/mp4",
                    server.url("/v1/audio/transcriptions").toString());
            fail("Expected IOException for 401 response");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("401"));
        }
    }

    @Test
    public void testNetworkFailure_returnsError() throws Exception {
        // Shut down server to simulate network failure
        server.shutdown();

        WhisperApiClient client = new WhisperApiClient("test-key", null);
        try {
            client.doTranscribeBlocking(
                    new byte[]{1, 2, 3}, "test.m4a", "audio/mp4",
                    server.url("/v1/audio/transcriptions").toString());
            fail("Expected IOException for network failure");
        } catch (IOException e) {
            // TC4: Verify error is thrown, not hung
            assertNotNull(e);
        }
    }

    @Test
    public void testEmptyResponse_returnsEmptyString() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"text\": \"\"}")
                .setResponseCode(200));

        WhisperApiClient client = new WhisperApiClient("test-key", null);
        String result = client.doTranscribeBlocking(
                new byte[]{1, 2, 3}, "test.m4a", "audio/mp4",
                server.url("/v1/audio/transcriptions").toString());

        assertEquals("", result);
    }

    @Test
    public void testNullTextInResponse_returnsEmptyString() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{}")
                .setResponseCode(200));

        WhisperApiClient client = new WhisperApiClient("test-key", null);
        String result = client.doTranscribeBlocking(
                new byte[]{1, 2, 3}, "test.m4a", "audio/mp4",
                server.url("/v1/audio/transcriptions").toString());

        assertEquals("", result);
    }
}
