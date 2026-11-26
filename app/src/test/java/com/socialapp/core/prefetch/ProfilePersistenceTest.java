package com.socialapp.core.prefetch;

import com.socialapp.core.prefetch.profile.NavigationProfile;
import com.socialapp.core.prefetch.profile.ProfileManager;
import com.socialapp.core.prefetch.profile.ProfileStore;
import com.socialapp.core.telemetry.TelemetryCollector;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

import static com.socialapp.core.prefetch.NavigationProfileTest.newEngine;
import static com.socialapp.core.prefetch.NavigationProfileTest.trace;
import static com.socialapp.core.prefetch.ScreenId.*;
import static org.junit.Assert.*;

public class ProfilePersistenceTest {
    @Rule public final TemporaryFolder folder = new TemporaryFolder();
    private final PrefetchEngine engine = newEngine();
    private final TelemetryCollector collector = new TelemetryCollector(() -> 100);
    private final Queue<Runnable> disk = new ArrayDeque<>();
    private final Queue<Runnable> main = new ArrayDeque<>();
    private final List<String> warnings = new ArrayList<>();
    private File file;
    private ProfileStore store;

    @Before
    public void createStore() {
        file = new File(folder.getRoot(), ProfileStore.LIVE_FILE_NAME);
        store = new ProfileStore(() -> file, (staged, target) -> Files.move(staged.toPath(), target.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    @After
    public void shutdown() {
        engine.getCache().shutdown();
    }

    @Test
    public void jsonRoundTripPreservesAllThreeTablesAndOverwritesTheCheckpoint() throws Exception {
        trace(engine, collector, FEED_NEWEST, POST_DETAIL, COMMENTS);
        NavigationProfile source = engine.snapshotProfile(7);
        store.write(source);
        String json = readJson();
        assertTrue(json.contains("\"schemaVersion\": 1"));
        assertTrue(json.contains("\"FEED_NEWEST\""));
        assertFalse(json.contains("navigationHistory"));
        assertFalse(json.contains("navigationId"));
        assertProfile(source, store.read().get());

        collector.confirmOutOfScopeScreen();
        trace(engine, collector, FEED_NEWEST, POST_DETAIL, COMMENTS);
        NavigationProfile updated = engine.snapshotProfile(8);
        store.write(updated);
        assertProfile(updated, store.read().get());
    }

    @Test
    public void failedReplacementLeavesThePreviousCheckpointReadable() throws Exception {
        store.write(engine.snapshotProfile(4));
        ProfileStore failing = new ProfileStore(() -> file, (staged, target) -> {
            throw new IOException("Replacement failed");
        });
        assertThrows(IOException.class, () -> failing.write(engine.snapshotProfile(5)));
        assertEquals(4, store.read().get().profileVersion);
        assertFalse(new File(file.getParentFile(), file.getName() + ".tmp").exists());
    }

    @Test
    public void missingFileKeepsWarmStartAndCheckpointIsAsynchronous() throws Exception {
        assertFalse(store.read().isPresent());
        ProfileManager manager = newManager();
        visit(manager, FEED_NEWEST, POST_DETAIL);
        assertEquals(6, feedToDetail());
        manager.saveLiveProfile();
        finishRead();
        assertEquals(7, feedToDetail());
        assertFalse(file.exists());
        disk.remove().run();
        assertEquals(1, store.read().get().profileVersion);
        assertTrue(warnings.isEmpty());
    }

    @Test
    public void corruptFileFallsBackToWarmStart() throws Exception {
        writeJson("{");
        ProfileManager manager = newManager();
        finishRead();
        assertEquals(6, feedToDetail());
        assertEquals(1, warnings.size());
        visit(manager, FEED_NEWEST, POST_DETAIL);
        manager.saveLiveProfile();
        disk.remove().run();
        assertEquals(7, store.read().get().order1.get(Collections.singletonList(FEED_NEWEST)).get(POST_DETAIL).intValue());
    }

    @Test
    public void rejectsUnsupportedSchemaUnknownScreensAndInvalidCounts() throws Exception {
        store.write(engine.snapshotProfile(4));
        String json = readJson();
        for (String invalid : new String[] {
                json.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"),
                json.replace("FEED_NEWEST", "UNKNOWN_SCREEN_ID"),
                json.replace("\"POST_DETAIL\": 6", "\"POST_DETAIL\": -1"),
                json.replace("\"POST_DETAIL\": 6", "\"POST_DETAIL\": 1.5"),
                json.replace("\"POST_DETAIL\": 6", "\"POST_DETAIL\": 2147483647"),
                json.replace("\"profileVersion\": 4", "\"profileVersion\": -1")
        }) {
            assertNotEquals(json, invalid);
            writeJson(invalid);
            assertThrows(RuntimeException.class, store::read);
        }
    }

    @Test
    public void replayStartsFromWarmStartThenRestoresLiveWithoutWritingReplayCounts() throws Exception {
        ProfileManager manager = newManager();
        finishRead();
        visit(manager, FEED_NEWEST, POST_DETAIL, FEED_NEWEST, POST_DETAIL);
        assertEquals(8, feedToDetail());
        manager.beginReplay();
        assertEquals(6, feedToDetail());
        collector.beginReplayRun();
        collector.beginReplaySession(1);
        visit(manager, FEED_NEWEST, POST_DETAIL, COMMENTS);
        assertEquals(7, feedToDetail());
        manager.saveLiveProfile();
        assertEquals(1, disk.size());
        disk.remove().run();
        String liveJson = readJson();

        collector.endReplayRun();
        manager.endReplay();
        assertEquals(8, feedToDetail());
        assertEquals(5, engine.getTransitionCount(Collections.singletonList(POST_DETAIL), COMMENTS));
        assertTrue(disk.isEmpty());
        assertEquals(liveJson, readJson());
        assertProfile(engine.snapshotProfile(1), store.read().get());
    }

    @Test
    public void startupReadCannotReplaceAnAlreadyRunningReplayModel() throws Exception {
        trace(engine, new TelemetryCollector(() -> 100), FEED_NEWEST, POST_DETAIL, FEED_NEWEST, POST_DETAIL);
        store.write(engine.snapshotProfile(10));
        engine.resetSession();
        ProfileManager manager = newManager();
        visit(manager, FEED_NEWEST, POST_DETAIL);
        manager.beginReplay();
        collector.beginReplayRun();
        collector.beginReplaySession(1);
        visit(manager, FEED_NEWEST, POST_DETAIL);
        finishRead();
        assertEquals(7, feedToDetail());
        assertEquals(10, store.read().get().profileVersion);

        collector.endReplayRun();
        manager.endReplay();
        assertEquals(9, feedToDetail());
        manager.saveLiveProfile();
        disk.remove().run();
        assertProfile(engine.snapshotProfile(11), store.read().get());
    }

    @Test
    public void checkpointOccursAfterTenLearnedTransitions() throws Exception {
        ProfileManager manager = newManager();
        finishRead();
        visit(manager, FEED_NEWEST);
        for (int i = 0; i < 9; i++) visit(manager, i % 2 == 0 ? POST_DETAIL : FEED_NEWEST);
        assertTrue(disk.isEmpty());
        visit(manager, FEED_NEWEST);
        assertEquals(1, disk.size());
        disk.remove().run();
        assertProfile(engine.snapshotProfile(1), store.read().get());
    }

    private ProfileManager newManager() {
        return new ProfileManager(engine, store, disk::add, main::add, warnings::add);
    }

    private void finishRead() {
        disk.remove().run();
        main.remove().run();
    }

    private void visit(ProfileManager manager, ScreenId... screens) {
        for (ScreenId screen : screens) manager.onNavigationEvent(collector.confirmScreen(screen).get());
    }

    private int feedToDetail() {
        return engine.getTransitionCount(Collections.singletonList(FEED_NEWEST), POST_DETAIL);
    }

    private void writeJson(String json) throws Exception {
        Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));
    }

    private String readJson() throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static void assertProfile(NavigationProfile expected, NavigationProfile actual) {
        assertEquals(expected.profileVersion, actual.profileVersion);
        assertEquals(expected.minSupport, actual.minSupport);
        assertEquals(expected.order1, actual.order1);
        assertEquals(expected.order2Backoff, actual.order2Backoff);
        assertEquals(expected.order2, actual.order2);
    }
}
