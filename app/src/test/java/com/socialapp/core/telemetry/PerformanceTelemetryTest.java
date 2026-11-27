package com.socialapp.core.telemetry;

import static org.junit.Assert.*;

import com.socialapp.core.prefetch.ScreenId;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

public class PerformanceTelemetryTest {
    @Rule public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void okHttpMeasuresBodiesAndKeepsTaggedContextWithoutWritingSensitiveValues() throws Exception {
        File file = new File(folder.getRoot(), "performance.csv");
        AtomicLong ticks = new AtomicLong(1000);
        PerformanceTelemetry telemetry = telemetry(file, ticks);
        NetworkTelemetry network = new NetworkTelemetry(ticks::getAndIncrement, telemetry);
        OkHttpClient client = new OkHttpClient.Builder().eventListenerFactory(network).build();
        TelemetryCollector collector = new TelemetryCollector(ticks::getAndIncrement);
        NavigationEvent navigation = collector.confirmScreen(ScreenId.POST_DETAIL).get();
        LoadContext load = collector.beginLoad(ScreenId.POST_DETAIL, LoadContext.Trigger.INITIAL, navigation);

        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("private-response"));
            String body = "private-request";
            Request request = new Request.Builder().url(server.url("/private-path?email=private-email"))
                    .header("Authorization", "Bearer private-token")
                    .post(RequestBody.create(body, MediaType.get("text/plain")))
                    .tag(LoadContext.class, load).build();
            try (Response response = client.newCall(request).execute()) {
                assertEquals("private-response", response.body().string());
            }
            Map<String, String> row = read(file).get(0);
            assertEquals("SUCCESS", row.get("outcome"));
            assertEquals("200", row.get("http_status"));
            assertEquals("REAL", row.get("network_mode"));
            assertEquals("NETWORK_CALL", row.get("event_type"));
            assertEquals(load.loadId.toString(), row.get("load_id"));
            assertEquals(navigation.navigationId.toString(), row.get("navigation_id"));
            assertEquals(load.runId.toString(), row.get("run_id"));
            assertEquals(load.sessionId.toString(), row.get("session_id"));
            assertEquals(Integer.toString(body.length()), row.get("request_body_bytes"));
            assertEquals(Integer.toString("private-response".length()), row.get("response_body_bytes"));
            assertEquals(number(row, "ended_elapsed_ms") - number(row, "started_elapsed_ms"),
                    number(row, "call_duration_ms"));
            assertTrue(number(row, "call_duration_ms") > 0);
            assertFalse(new String(Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8).contains("private-"));
        }
    }

    @Test
    public void failureAndCancellationAreDistinctAndMissingSizesStayBlank() throws Exception {
        File file = new File(folder.getRoot(), "performance.csv");
        AtomicLong ticks = new AtomicLong(42);
        NetworkTelemetry network = new NetworkTelemetry(ticks::get, telemetry(file, ticks));
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url("http://example.invalid/hidden").build();

        Call failed = client.newCall(request);
        EventListener listener = network.create(failed);
        listener.callStart(failed);
        network.markSimulated(failed);
        ticks.set(65);
        listener.callFailed(failed, new IOException("sensitive-detail"));
        listener.callEnd(failed); // A duplicate terminal cannot publish twice.

        Call cancelled = client.newCall(request);
        listener = network.create(cancelled);
        listener.callStart(cancelled);
        cancelled.cancel();
        ticks.set(84);
        listener.callFailed(cancelled, new IOException("sensitive-detail"));

        List<Map<String, String>> rows = read(file);
        assertEquals(2, rows.size());
        assertEquals("FAILURE", rows.get(0).get("outcome"));
        assertEquals("SIMULATED", rows.get(0).get("network_mode"));
        assertEquals("23", rows.get(0).get("call_duration_ms"));
        assertEquals("CANCELLED", rows.get(1).get("outcome"));
        assertEquals("19", rows.get(1).get("call_duration_ms"));
        assertEquals("", rows.get(1).get("request_body_bytes"));
        assertEquals("", rows.get(1).get("response_body_bytes"));
        assertEquals("", rows.get(1).get("navigation_id"));
        assertNotEquals(rows.get(0).get("request_id"), rows.get(1).get("request_id"));
    }

    @Test
    public void checkpointFlushesBatchAndReportsDroppedRows() throws Exception {
        File file = new File(folder.getRoot(), "performance.csv");
        Queue<Runnable> disk = new ArrayDeque<>();
        AtomicInteger fileResolutions = new AtomicInteger();
        TelemetryCsvWriter writer = new TelemetryCsvWriter(disk::add, 2, ignored -> {});
        PerformanceTelemetry telemetry = new PerformanceTelemetry(writer, () -> {
            fileResolutions.incrementAndGet();
            return file;
        }, () -> 80);
        telemetry.homeFullyDrawn(ScreenId.FEED_NEWEST, 20);
        telemetry.contentReady(ScreenId.FACT_CHECK, new NavigationTiming(18), null, 31);
        telemetry.homeFullyDrawn(ScreenId.FEED_NEWEST, 32);
        telemetry.checkpoint();
        assertEquals(1, disk.size());
        assertFalse(file.exists());
        disk.remove().run();
        assertEquals(2, fileResolutions.get()); // One write for two rows, one for the checkpoint.
        List<Map<String, String>> rows = read(file);
        assertEquals(3, rows.size());
        assertEquals("13", rows.get(1).get("navigation_content_ready_ms"));
        assertEquals("CHECKPOINT", rows.get(2).get("event_type"));
        assertEquals("1", rows.get(2).get("buffer_rows_dropped"));
    }

    @Test
    public void frameSummaryUsesOnlyKnownDeadlinesAndKeepsPlatformDropsSeparate() throws Exception {
        File file = new File(folder.getRoot(), "performance.csv");
        FrameSummary frames = new FrameSummary();
        frames.add(8_000_000, 9_000_000, 2);
        frames.add(11_000_000, 10_000_000, 1);
        frames.add(13_000_000, -1, 0);
        frames.add(-1, -1, 0);
        assertEquals(0.5, frames.jankRate(), 0);
        assertNull(new FrameSummary().jankRate());

        telemetry(file, new AtomicLong()).frames(PerformanceTelemetry.Screen.HOME, 10, 80, frames);
        Map<String, String> row = read(file).get(0);
        assertEquals("4", row.get("frames_observed"));
        assertEquals("2", row.get("frames_with_deadline"));
        assertEquals("1", row.get("jank_frames"));
        assertEquals("3", row.get("frame_reports_dropped"));
        assertEquals("13.0", row.get("max_frame_duration_ms"));
        assertEquals(32 / 3.0, Double.parseDouble(row.get("mean_frame_duration_ms")), 0.000001);
    }

    @Test
    public void contentReadyIsReportedAtMostOncePerRequest() {
        NavigationTiming timing = new NavigationTiming(100);
        assertFalse(timing.markReady(99));
        assertTrue(timing.markReady(103));
        assertFalse(timing.markReady(130));
    }

    private static PerformanceTelemetry telemetry(File file, AtomicLong ticks) {
        return new PerformanceTelemetry(new TelemetryCsvWriter(Runnable::run, 16, ignored -> {}), () -> file,
                ticks::get);
    }

    private static long number(Map<String, String> row, String key) {
        return Long.parseLong(row.get(key));
    }

    private static List<Map<String, String>> read(File file) throws IOException {
        List<String> lines = Files.readAllLines(file.toPath());
        String[] columns = lines.remove(0).split(",", -1);
        List<Map<String, String>> rows = new ArrayList<>();
        for (String line : lines) {
            String[] cells = line.split(",", -1);
            assertEquals(columns.length, cells.length);
            Map<String, String> row = new HashMap<>();
            for (int i = 0; i < columns.length; i++) row.put(columns[i], cells[i]);
            rows.add(row);
        }
        return rows;
    }
}
