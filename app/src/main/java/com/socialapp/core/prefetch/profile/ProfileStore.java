package com.socialapp.core.prefetch.profile;

import android.system.ErrnoException;
import android.system.Os;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.socialapp.core.prefetch.ScreenId;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** JSON storage for one LIVE checkpoint. Call from the profile I/O executor. */
public final class ProfileStore {
    @FunctionalInterface
    public interface ReplaceFile {
        void replace(File staged, File target) throws IOException;
    }

    public static final String LIVE_FILE_NAME = "navigation_profile_live.json";
    private final Supplier<File> path;
    private final ReplaceFile replaceFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public ProfileStore(Supplier<File> path) {
        this(path, ProfileStore::replaceOnAndroid);
    }

    public ProfileStore(Supplier<File> path, ReplaceFile replaceFile) {
        this.path = path;
        this.replaceFile = replaceFile;
    }

    public Optional<NavigationProfile> read() throws IOException {
        File file = path.get();
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            return Optional.of(decode(JsonParser.parseReader(reader).getAsJsonObject()));
        } catch (FileNotFoundException missing) {
            if (file.exists()) throw missing;
            return Optional.empty();
        }
    }

    public void write(NavigationProfile profile) throws IOException {
        File file = path.get();
        File staged = new File(file.getParentFile(), file.getName() + ".tmp");
        byte[] json = (gson.toJson(encode(profile)) + "\n").getBytes(StandardCharsets.UTF_8);
        try {
            try (FileOutputStream output = new FileOutputStream(staged)) {
                output.write(json);
                output.getFD().sync();
            }
            replaceFile.replace(staged, file);
        } finally {
            // With a single writer, a leftover staging file can be overwritten on the next save.
            if (staged.exists()) staged.delete();
        }
    }

    private static void replaceOnAndroid(File staged, File target) throws IOException {
        try {
            Os.rename(staged.getAbsolutePath(), target.getAbsolutePath());
        } catch (ErrnoException error) {
            throw new IOException("Unable to replace the LIVE profile", error);
        }
    }

    private static JsonObject encode(NavigationProfile profile) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", profile.schemaVersion);
        root.addProperty("profileVersion", profile.profileVersion);
        JsonObject model = new JsonObject();
        model.addProperty("maxOrder", NavigationProfile.MAX_ORDER);
        model.addProperty("minSupport", profile.minSupport);
        model.addProperty("learning", NavigationProfile.LEARNING_STRATEGY);
        root.add("model", model);
        root.add("order1", encodeTable(profile.order1));
        root.add("order2Backoff", encodeTable(profile.order2Backoff));
        root.add("order2", encodeTable(profile.order2));
        return root;
    }

    private static JsonArray encodeTable(Map<List<ScreenId>, Map<ScreenId, Integer>> table) {
        List<Map.Entry<List<ScreenId>, Map<ScreenId, Integer>>> rows = new ArrayList<>(table.entrySet());
        rows.sort(Comparator.comparing(row -> contextKey(row.getKey())));
        JsonArray result = new JsonArray();
        for (Map.Entry<List<ScreenId>, Map<ScreenId, Integer>> row : rows) {
            JsonObject object = new JsonObject();
            JsonArray context = new JsonArray();
            for (ScreenId screen : row.getKey()) context.add(screen.getId());
            object.add("context", context);
            JsonObject counts = new JsonObject();
            row.getValue().entrySet().stream().sorted(Comparator.comparing(next -> next.getKey().getId()))
                    .forEach(next -> counts.addProperty(next.getKey().getId(), next.getValue()));
            object.add("counts", counts);
            result.add(object);
        }
        return result;
    }

    private static String contextKey(List<ScreenId> context) {
        return context.size() == 1 ? context.get(0).getId() : context.get(0).getId() + "/" + context.get(1).getId();
    }

    private static NavigationProfile decode(JsonObject root) {
        if (readInteger(root.get("schemaVersion")) != NavigationProfile.SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported profile schema");
        }
        JsonObject model = root.getAsJsonObject("model");
        if (model == null || readInteger(model.get("maxOrder")) != NavigationProfile.MAX_ORDER
                || !NavigationProfile.LEARNING_STRATEGY.equals(readString(model.get("learning")))) {
            throw new IllegalArgumentException("Unsupported profile model");
        }
        return new NavigationProfile(readInteger(root.get("profileVersion")),
                Math.toIntExact(readInteger(model.get("minSupport"))),
                decodeTable(root.getAsJsonArray("order1")), decodeTable(root.getAsJsonArray("order2Backoff")),
                decodeTable(root.getAsJsonArray("order2")));
    }

    private static Map<List<ScreenId>, Map<ScreenId, Integer>> decodeTable(JsonArray table) {
        if (table == null) throw new IllegalArgumentException("Missing profile table");
        Map<List<ScreenId>, Map<ScreenId, Integer>> result = new LinkedHashMap<>();
        for (JsonElement element : table) {
            JsonObject row = element.getAsJsonObject();
            JsonArray inputContext = row.getAsJsonArray("context");
            JsonObject inputCounts = row.getAsJsonObject("counts");
            if (inputContext == null || inputCounts == null) throw new IllegalArgumentException("Missing profile row fields");
            List<ScreenId> context = new ArrayList<>();
            for (JsonElement screen : inputContext) context.add(ScreenId.fromReplayId(readString(screen)));
            Map<ScreenId, Integer> counts = new EnumMap<>(ScreenId.class);
            for (Map.Entry<String, JsonElement> next : inputCounts.entrySet()) {
                counts.put(ScreenId.fromReplayId(next.getKey()), Math.toIntExact(readInteger(next.getValue())));
            }
            if (result.putIfAbsent(context, counts) != null) throw new IllegalArgumentException("Duplicate profile context");
        }
        return result;
    }

    private static long readInteger(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Expected integer profile field");
        }
        return element.getAsBigDecimal().longValueExact();
    }

    private static String readString(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Expected string profile field");
        }
        return element.getAsString();
    }
}
