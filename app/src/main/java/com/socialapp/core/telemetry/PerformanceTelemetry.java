package com.socialapp.core.telemetry;

import com.socialapp.core.prefetch.ScreenId;

import java.io.File;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Separate CSV schema for performance observations. Only structured, allowlisted values enter it. */
public final class PerformanceTelemetry {
    public static final String FILE_NAME = "performance_v1.csv";
    static final String HEADER = "schema_version,process_id,event_type,request_id,run_id,session_id,"
            + "replay_session_id,navigation_id,load_id,event_source,screen,network_mode,outcome,http_status,"
            + "started_elapsed_ms,ended_elapsed_ms,call_duration_ms,dns_ms,connect_ms,tls_ms,"
            + "request_body_ms,response_body_ms,request_body_bytes,response_body_bytes,journey_id,"
            + "navigation_content_ready_ms,frames_observed,frames_with_deadline,jank_frames,jank_rate,"
            + "mean_frame_duration_ms,max_frame_duration_ms,frame_reports_dropped,buffer_rows_dropped,write_rows_failed\n";
    private static final int COLUMNS = 35;

    public enum Screen { HOME, FEED_NEWEST, FEED_HOT, FEED_FOLLOWING, SEARCH, PROFILE, FACT_CHECK, POST_DETAIL,
        COMMENTS, SETTINGS, OTHER }

    private final UUID processId = UUID.randomUUID();
    private final TelemetryCsvWriter writer;
    private final TelemetryCsvWriter.Stream stream;
    private final LongSupplier clock;

    public PerformanceTelemetry(TelemetryCsvWriter writer, Supplier<File> output, LongSupplier clock) {
        this.writer = writer;
        this.stream = new TelemetryCsvWriter.Stream(output, HEADER);
        this.clock = clock;
    }

    void network(UUID request, LoadContext load, boolean simulated, NetworkTelemetry.Outcome outcome,
                 Integer httpStatus, long start, long end, Long dns, Long connect, Long tls,
                 Long requestBodyMs, Long responseBodyMs, Long requestBytes, Long responseBytes) {
        write("NETWORK_CALL", request, load == null ? null : load.runId, load == null ? null : load.sessionId,
                load == null ? null : load.replaySessionId, load == null ? null : load.navigationId,
                load == null ? null : load.loadId, load == null ? null : load.source, load == null ? null : load.screen,
                simulated ? "SIMULATED" : "REAL", outcome, httpStatus,
                start, end, end - start, dns, connect, tls, requestBodyMs, responseBodyMs, requestBytes, responseBytes);
    }

    public void contentReady(ScreenId screen, NavigationTiming timing, NavigationEvent event, long end) {
        write("NAVIGATION_CONTENT_READY", null, event == null ? null : event.runId,
                event == null ? null : event.sessionId, event == null ? null : event.replaySessionId,
                event == null ? null : event.navigationId, null, event == null ? null : event.source,
                screen, null, null, null, timing.startedAtMs, end, null, null, null, null, null, null, null, null,
                timing.id, end - timing.startedAtMs);
    }

    public void homeFullyDrawn(ScreenId screen, long at) {
        write("HOME_FULLY_DRAWN", null, null, null, null, null, null, null, screen,
                null, null, null, null, at);
    }

    void frames(Screen screen, long start, long end, FrameSummary frames) {
        write("FRAME_SUMMARY", null, null, null, null, null, null, null, screen,
                null, null, null, start, end, null, null, null, null, null, null, null, null, null, null,
                frames.observed, frames.withDeadline, frames.withDeadline == 0 ? null : frames.jank,
                frames.jankRate(), frames.meanMs(), frames.maxMs(), frames.platformDrops);
    }

    public void checkpoint() {
        writer.checkpoint(stream, () -> row("CHECKPOINT", null, null, null, null, null, null, null, null, null,
                null, null, null, clock.getAsLong()));
    }

    private void write(String type, Object... data) {
        writer.offer(stream, row(type, data));
    }

    private String row(String type, Object... data) {
        StringBuilder line = new StringBuilder(180).append("1,").append(processId).append(',').append(type);
        for (int column = 3; column < COLUMNS - 2; column++) {
            line.append(',');
            int index = column - 3;
            if (index < data.length && data[index] != null) line.append(data[index]);
        }
        return line.append(',').append(stream.dropped()).append(',').append(stream.failed()).append('\n').toString();
    }
}
