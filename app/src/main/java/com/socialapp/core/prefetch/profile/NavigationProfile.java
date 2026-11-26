package com.socialapp.core.prefetch.profile;

import com.socialapp.core.prefetch.ScreenId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable model counts. Navigation context belongs to the running engine. */
public final class NavigationProfile {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_ORDER = 2;
    public static final String LEARNING_STRATEGY = "CUMULATIVE";

    public final int schemaVersion = SCHEMA_VERSION;
    public final long profileVersion;
    public final int minSupport;
    public final Map<List<ScreenId>, Map<ScreenId, Integer>> order1;
    public final Map<List<ScreenId>, Map<ScreenId, Integer>> order2Backoff;
    public final Map<List<ScreenId>, Map<ScreenId, Integer>> order2;

    public NavigationProfile(long profileVersion, int minSupport,
                             Map<List<ScreenId>, Map<ScreenId, Integer>> order1,
                             Map<List<ScreenId>, Map<ScreenId, Integer>> order2Backoff,
                             Map<List<ScreenId>, Map<ScreenId, Integer>> order2) {
        if (profileVersion < 0 || minSupport < 1) throw new IllegalArgumentException("Invalid profile metadata");
        this.profileVersion = profileVersion;
        this.minSupport = minSupport;
        this.order1 = freeze(order1, 1);
        this.order2Backoff = freeze(order2Backoff, 1);
        this.order2 = freeze(order2, 2);
    }

    private static Map<List<ScreenId>, Map<ScreenId, Integer>> freeze(
            Map<List<ScreenId>, Map<ScreenId, Integer>> table, int order) {
        if (table == null) throw new IllegalArgumentException("Missing profile table");
        Map<List<ScreenId>, Map<ScreenId, Integer>> copy = new LinkedHashMap<>();
        for (Map.Entry<List<ScreenId>, Map<ScreenId, Integer>> row : table.entrySet()) {
            List<ScreenId> context = row.getKey();
            if (context == null || context.size() != order || context.stream().anyMatch(Objects::isNull)
                    || row.getValue() == null || row.getValue().isEmpty()) {
                throw new IllegalArgumentException("Invalid profile row");
            }
            Map<ScreenId, Integer> counts = new EnumMap<>(ScreenId.class);
            long total = 0;
            for (Map.Entry<ScreenId, Integer> next : row.getValue().entrySet()) {
                Integer count = next.getValue();
                if (next.getKey() == null || count == null || count <= 0) {
                    throw new IllegalArgumentException("Invalid profile transition");
                }
                total += count;
                counts.put(next.getKey(), count);
            }
            if (total > Integer.MAX_VALUE) throw new IllegalArgumentException("Profile row total exceeds integer range");
            copy.put(Collections.unmodifiableList(new ArrayList<>(context)), Collections.unmodifiableMap(counts));
        }
        return Collections.unmodifiableMap(copy);
    }
}
