package com.socialapp.core.prefetch;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import org.junit.Test;

import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ReplayParserTest {

    @Test
    public void t4PresetUsesThePinnedGuestCalibrationSessionsInOrder() throws Exception {
        List<ReplayEngine.ReplaySession> sessions;
        Path asset = Paths.get("src", "main", "assets", ReplayEngine.DEFAULT_ASSET_FILE);
        try (Reader reader = Files.newBufferedReader(asset, StandardCharsets.UTF_8)) {
            sessions = ReplayEngine.parseT4PresetSessions(reader, ReplayEngine.T4Preset.CALIBRATION);
        }
        List<Integer> ids = sessions.stream().map(session -> session.sessionId).collect(Collectors.toList());
        assertEquals(Arrays.asList(12, 13, 17, 18, 23, 31, 41, 46, 52, 53), ids);
        assertEquals(50, sessions.stream().mapToInt(session -> session.steps.size()).sum());
        assertFalse(ReplayEngine.requiresLogin(sessions, 0));
        assertFalse(sessions.stream().flatMap(session -> session.steps.stream())
                .anyMatch(step -> step.screen == ScreenId.SETTINGS));

        Path manifest = Paths.get("..", "scripts", "replay_workload_t3.json");
        try (Reader reader = Files.newBufferedReader(manifest, StandardCharsets.UTF_8)) {
            JsonObject split = JsonParser.parseReader(reader).getAsJsonObject();
            for (Integer id : ids) {
                assertTrue(split.getAsJsonArray("calibration_session_ids").contains(new JsonPrimitive(id)));
                assertFalse(split.getAsJsonArray("evaluation_session_ids").contains(new JsonPrimitive(id)));
            }
        }
    }

    @Test
    public void t4PresetFailsClosedWhenRequiredSessionsAreMissing() {
        assertThrows(IllegalArgumentException.class, () -> ReplayEngine.parseT4PresetSessions(
                new StringReader("{\"sessions\":[{\"session_id\":12,\"steps\":[{\"screen\":\"FEED_NEWEST\"}]}]}"),
                ReplayEngine.T4Preset.CALIBRATION));
    }

    @Test
    public void t4EvaluationUsesAllAndOnlyTheManifestEvaluationSessionsInOrder() throws Exception {
        List<ReplayEngine.ReplaySession> sessions;
        try (Reader reader = Files.newBufferedReader(Paths.get("src", "main", "assets", ReplayEngine.DEFAULT_ASSET_FILE),
                StandardCharsets.UTF_8)) {
            sessions = ReplayEngine.parseT4PresetSessions(reader, ReplayEngine.T4Preset.EVALUATION);
        }
        try (Reader reader = Files.newBufferedReader(Paths.get("..", "scripts", "replay_workload_t3.json"),
                StandardCharsets.UTF_8)) {
            JsonObject split = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray expected = split.getAsJsonArray("evaluation_session_ids");
            assertEquals(21, sessions.size());
            assertEquals(expected.size(), sessions.size());
            for (int index = 0; index < sessions.size(); index++) {
                int id = sessions.get(index).sessionId;
                assertEquals(expected.get(index).getAsInt(), id);
                assertFalse(split.getAsJsonArray("calibration_session_ids").contains(new JsonPrimitive(id)));
            }
        }
        assertEquals(109, sessions.stream().mapToInt(session -> session.steps.size()).sum());
        assertFalse(ReplayEngine.requiresLogin(sessions, 0));
        assertFalse(sessions.stream().flatMap(session -> session.steps.stream())
                .anyMatch(step -> step.screen == ScreenId.SETTINGS));
    }

    @Test
    public void assetScreenIdsDecodeWithoutChangingDestinations() throws Exception {
        Path asset = Paths.get("src", "main", "assets", ReplayEngine.DEFAULT_ASSET_FILE);
        List<ReplayEngine.ReplaySession> decoded;
        try (Reader reader = Files.newBufferedReader(asset, StandardCharsets.UTF_8)) {
            decoded = ReplayEngine.parseSessions(reader);
        }
        assertEquals(100, decoded.size());

        try (Reader reader = Files.newBufferedReader(asset, StandardCharsets.UTF_8)) {
            JsonArray rawSessions = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("sessions");
            assertEquals(rawSessions.size(), decoded.size());

            int stepCount = 0;
            for (int i = 0; i < rawSessions.size(); i++) {
                JsonObject rawSession = rawSessions.get(i).getAsJsonObject();
                ReplayEngine.ReplaySession session = decoded.get(i);
                assertEquals(rawSession.get("session_id").getAsInt(), session.sessionId);
                JsonArray rawSteps = rawSession.getAsJsonArray("steps");
                assertEquals("Steps in session " + session.sessionId, rawSteps.size(), session.steps.size());

                for (int j = 0; j < rawSteps.size(); j++) {
                    JsonObject rawStep = rawSteps.get(j).getAsJsonObject();
                    ReplayEngine.ReplayStep step = session.steps.get(j);
                    String location = "Session " + session.sessionId + ", step " + (j + 1);
                    assertEquals(location, rawStep.get("step").getAsInt(), step.step);
                    assertEquals(location, rawStep.get("dwell_ms").getAsLong(), step.dwellMs);
                    assertEquals(location, rawStep.get("screen").getAsString(), step.screen.getId());
                    stepCount++;
                }
            }
            assertEquals(514, stepCount);
        }
    }

    @Test
    public void replayIdLookupAcceptsEveryDeclaredId() {
        assertEquals(9, ScreenId.values().length);
        for (ScreenId screen : ScreenId.values()) {
            assertSame(screen, ScreenId.fromReplayId(screen.getId()));
        }
    }

    @Test
    public void replayIdLookupRejectsInvalidIdsAndRoutes() {
        for (String value : new String[]{null, "", " ", "UNKNOWN", "post_detail", "post/1", "home?tab=hot"}) {
            assertThrows(IllegalArgumentException.class, () -> ScreenId.fromReplayId(value));
        }
        assertSame(ScreenId.POST_DETAIL, ScreenId.fromRoute("post/1"));
        assertSame(ScreenId.FEED_HOT, ScreenId.fromRoute("home?tab=hot"));
    }

    @Test
    public void lateInvalidStepRejectsWholeTrace() {
        String json = "{\"sessions\":["
                + "{\"session_id\":8,\"steps\":[{\"screen\":\"FEED_NEWEST\"}]},"
                + "{\"session_id\":9,\"steps\":[{\"screen\":\"POST_DETAIL\"},"
                + "{\"step\":4,\"screen\":\"UNKNOWN\"}]}]}";

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ReplayEngine.parseSessions(new StringReader(json)));

        assertTrue(error.getMessage().contains("UNKNOWN"));
        assertTrue(error.getMessage().contains("session 9, step 4"));
    }

    @Test
    public void rejectsMissingOrNonStringScreenValues() {
        for (String step : new String[]{"{}", "{\"screen\":null}", "{\"screen\":12}"}) {
            String json = "{\"sessions\":[{\"steps\":[" + step + "]}]}";
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> ReplayEngine.parseSessions(new StringReader(json)));
            assertTrue(error.getMessage().contains("session 1, step 1"));
        }
    }

    @Test
    public void rejectsMissingSessionsOrStepsArray() {
        for (String json : new String[]{"{}", "{\"sessions\":[{\"session_id\":8}]}"}) {
            assertThrows(IllegalArgumentException.class, () -> ReplayEngine.parseSessions(new StringReader(json)));
        }
    }

    @Test
    public void keepsDefaultsForOptionalStepAndSessionFields() {
        String json = "{\"sessions\":[{\"steps\":[{\"screen\":\"FACT_CHECK\"}]}]}";

        ReplayEngine.ReplaySession session = ReplayEngine.parseSessions(new StringReader(json)).get(0);
        ReplayEngine.ReplayStep step = session.steps.get(0);

        assertEquals(1, session.sessionId);
        assertEquals(1, step.step);
        assertEquals(2000, step.dwellMs);
        assertSame(ScreenId.FACT_CHECK, step.screen);
    }

    @Test
    public void followingCheckOnlyScansSelectedSessions() {
        String json = "{\"sessions\":["
                + "{\"steps\":[{\"screen\":\"FEED_HOT\"}]},"
                + "{\"steps\":[{\"screen\":\"FEED_FOLLOWING\"}]}]}";
        List<ReplayEngine.ReplaySession> sessions = ReplayEngine.parseSessions(new StringReader(json));

        assertFalse(ReplayEngine.requiresLogin(sessions, 1));
        assertTrue(ReplayEngine.requiresLogin(sessions, 2));
        assertTrue(ReplayEngine.requiresLogin(sessions, 100));
        assertTrue(ReplayEngine.requiresLogin(sessions, 0));
    }
}
