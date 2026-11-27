package com.socialapp.core.telemetry;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Bounded, single-consumer CSV queue. Its monitor protects scheduling, never filesystem work. */
public final class TelemetryCsvWriter {
    private static final int BATCH_SIZE = 64;
    private static final int TERMINAL_CAPACITY = 8;

    public static final class Stream {
        final Supplier<File> file;
        final String header;
        private final AtomicLong dropped = new AtomicLong();
        private final AtomicLong failed = new AtomicLong();

        public Stream(Supplier<File> file, String header) {
            this.file = file;
            this.header = header;
        }

        public long dropped() { return dropped.get(); }
        public long failed() { return failed.get(); }
    }

    public static final class Terminal {
        private final Stream stream;
        private boolean queued;

        private Terminal(Stream stream) {
            this.stream = stream;
        }
    }

    private static final class Entry {
        final Stream stream;
        final String row;
        final Supplier<String> control;
        final Consumer<Boolean> written;

        Entry(Stream stream, String row, Supplier<String> control, Consumer<Boolean> written) {
            this.stream = stream;
            this.row = row;
            this.control = control;
            this.written = written;
        }
    }

    private final ArrayDeque<Entry> queue = new ArrayDeque<>();
    private final Executor executor;
    private final Consumer<String> warning;
    private final int capacity;
    private int queuedRows;
    private int reservedTerminals;
    private boolean draining;
    private Entry pendingCheckpoint;

    public TelemetryCsvWriter(Executor executor, int capacity, Consumer<String> warning) {
        if (capacity < 1) throw new IllegalArgumentException("capacity");
        this.executor = executor;
        this.capacity = capacity;
        this.warning = warning;
    }

    public boolean offer(Stream stream, String row) {
        boolean schedule;
        long drops = 0;
        synchronized (this) {
            if (queuedRows >= capacity) {
                drops = stream.dropped.incrementAndGet();
                schedule = false;
            } else {
                queue.addLast(new Entry(stream, row, null, null));
                queuedRows++;
                schedule = markScheduled();
            }
        }
        if (drops != 0) {
            if ((drops & (drops - 1)) == 0) warning.accept("CSV buffer full: dropped rows for stream=" + drops);
            return false;
        }
        if (schedule) executor.execute(this::drain);
        return true;
    }

    public synchronized Terminal reserveTerminal(Stream stream) {
        if (reservedTerminals == TERMINAL_CAPACITY) return null;
        reservedTerminals++;
        return new Terminal(stream);
    }

    public void finish(Terminal terminal, Supplier<String> row, Consumer<Boolean> written) {
        boolean schedule;
        synchronized (this) {
            if (terminal.queued) throw new IllegalStateException("Terminal already queued");
            terminal.queued = true;
            queue.addLast(new Entry(terminal.stream, null, row, written));
            schedule = markScheduled();
        }
        if (schedule) executor.execute(this::drain);
    }

    /** One coalesced checkpoint is reserved independently of normal rows and replay terminals. */
    public void checkpoint(Stream stream, Supplier<String> row) {
        boolean schedule;
        synchronized (this) {
            if (pendingCheckpoint != null) queue.remove(pendingCheckpoint);
            pendingCheckpoint = new Entry(stream, null, row, null);
            queue.addLast(pendingCheckpoint);
            schedule = markScheduled();
        }
        if (schedule) executor.execute(this::drain);
    }

    private boolean markScheduled() {
        if (draining) return false;
        draining = true;
        return true;
    }

    private void drain() {
        List<Entry> batch = new ArrayList<>(BATCH_SIZE);
        while (true) {
            synchronized (this) {
                if (queue.isEmpty()) {
                    draining = false;
                    return;
                }
                while (batch.size() < BATCH_SIZE && !queue.isEmpty()) {
                    Entry entry = queue.removeFirst();
                    if (entry.control == null) queuedRows--;
                    if (entry == pendingCheckpoint) pendingCheckpoint = null;
                    batch.add(entry);
                }
            }
            Map<Stream, List<Entry>> groups = new LinkedHashMap<>();
            for (Entry entry : batch) {
                if (entry.control == null) {
                    groups.computeIfAbsent(entry.stream, ignored -> new ArrayList<>()).add(entry);
                    continue;
                }
                writeGroups(groups);
                boolean success = write(entry.stream, java.util.Collections.singletonList(entry));
                if (entry.written != null) {
                    synchronized (this) { reservedTerminals--; }
                    entry.written.accept(success);
                }
            }
            writeGroups(groups);
            batch.clear();
        }
    }

    private void writeGroups(Map<Stream, List<Entry>> groups) {
        for (Map.Entry<Stream, List<Entry>> group : groups.entrySet()) write(group.getKey(), group.getValue());
        groups.clear();
    }

    private boolean write(Stream stream, List<Entry> entries) {
        try {
            File file = stream.file.get();
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
                throw new IOException("CSV directory unavailable");
            }
            boolean needsHeader = file.length() == 0;
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
                if (needsHeader) writer.write(stream.header);
                for (Entry entry : entries) writer.write(entry.control == null ? entry.row : entry.control.get());
            }
            return true;
        } catch (IOException | SecurityException error) {
            stream.failed.addAndGet(entries.size());
            warning.accept("CSV write failed: " + error.getClass().getSimpleName());
            return false;
        }
    }
}
