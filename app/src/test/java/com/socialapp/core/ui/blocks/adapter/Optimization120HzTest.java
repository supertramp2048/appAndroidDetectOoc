package com.socialapp.core.ui.blocks.adapter;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.request.RequestOptions;
import com.socialapp.core.util.DateTimeUtils;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class Optimization120HzTest {

    @Test
    public void testComputePrefetchRangeDownwardAndClamping() {
        assertArrayEquals(new int[]{0, 0}, BlockScrollPreloader.computePrefetchRange(0, 3, 0, 10, 10));
        assertArrayEquals(new int[]{0, 0}, BlockScrollPreloader.computePrefetchRange(-1, 3, 20, 10, 10));
        assertArrayEquals(new int[]{0, 0}, BlockScrollPreloader.computePrefetchRange(5, 3, 20, 10, 10));

        // Downward scroll inside list
        assertArrayEquals(new int[]{5, 15}, BlockScrollPreloader.computePrefetchRange(0, 4, 30, 40, 10));

        // Downward scroll clamped at end
        assertArrayEquals(new int[]{25, 30}, BlockScrollPreloader.computePrefetchRange(20, 24, 30, 40, 10));

        // Already at last item
        assertArrayEquals(new int[]{0, 0}, BlockScrollPreloader.computePrefetchRange(25, 29, 30, 40, 10));
    }

    @Test
    public void testComputePrefetchRangeUpwardAndClamping() {
        // Upward scroll inside list
        assertArrayEquals(new int[]{2, 12}, BlockScrollPreloader.computePrefetchRange(12, 16, 30, -35, 10));

        // Upward scroll clamped at 0
        assertArrayEquals(new int[]{0, 4}, BlockScrollPreloader.computePrefetchRange(4, 8, 30, -35, 10));

        // Already at top item
        assertArrayEquals(new int[]{0, 0}, BlockScrollPreloader.computePrefetchRange(0, 4, 30, -35, 10));
    }

    @Test
    public void testShouldPauseMediaForFlingOnlyDuringFastSettling() {
        int threshold = 420;
        assertTrue(BlockScrollPreloader.shouldPauseMediaForFling(RecyclerView.SCROLL_STATE_SETTLING, 420, threshold));
        assertTrue(BlockScrollPreloader.shouldPauseMediaForFling(RecyclerView.SCROLL_STATE_SETTLING, 800, threshold));

        assertFalse(BlockScrollPreloader.shouldPauseMediaForFling(RecyclerView.SCROLL_STATE_SETTLING, 419, threshold));
        assertFalse(BlockScrollPreloader.shouldPauseMediaForFling(RecyclerView.SCROLL_STATE_DRAGGING, 800, threshold));
        assertFalse(BlockScrollPreloader.shouldPauseMediaForFling(RecyclerView.SCROLL_STATE_IDLE, 800, threshold));
        assertFalse(BlockScrollPreloader.shouldPauseMediaForFling(RecyclerView.SCROLL_STATE_SETTLING, 800, 0));
    }

    @Test
    public void testBlockImageOptionsDeterministicKeyAndReuse() {
        assertEquals("312x234_r12", BlockImageOptions.buildThumbnailKey(312, 234, 12));
        assertEquals("1x1_r1", BlockImageOptions.buildThumbnailKey(0, -5, 0));
        assertEquals("avatar_66", BlockImageOptions.buildAvatarKey(66));
        assertEquals("avatar_1", BlockImageOptions.buildAvatarKey(0));

        RequestOptions thumbA = BlockImageOptions.feedThumbnailOptions(312, 234, 12);
        RequestOptions thumbB = BlockImageOptions.feedThumbnailOptions(312, 234, 12);
        assertSame(thumbA, thumbB);

        RequestOptions avatarA = BlockImageOptions.avatarOptions(66);
        RequestOptions avatarB = BlockImageOptions.avatarOptions(66);
        assertSame(avatarA, avatarB);
    }

    @Test
    public void testDateTimeUtilsFormatsAndCachedParsing() {
        assertNull(DateTimeUtils.parseDate(null));
        assertNull(DateTimeUtils.parseDate("   "));
        assertNull(DateTimeUtils.parseDate("not-a-timestamp"));
        assertEquals("not-a-timestamp", DateTimeUtils.formatRelativeTime("not-a-timestamp"));
        assertEquals("", DateTimeUtils.formatRelativeTime(" "));

        Date isoMillis = DateTimeUtils.parseDate("2025-04-10T08:15:30.123Z");
        Date isoMicros = DateTimeUtils.parseDate("2025-04-10T08:15:30.123456Z");
        Date isoSec = DateTimeUtils.parseDate("2025-04-10T08:15:30Z");
        Date sqlDate = DateTimeUtils.parseDate("2025-04-10 08:15:30");

        assertNotNull(isoMillis);
        assertNotNull(isoMicros);
        assertNotNull(isoSec);
        assertNotNull(sqlDate);
        assertEquals(isoMillis.getTime(), isoMicros.getTime());
        assertEquals(isoSec.getTime(), sqlDate.getTime());

        // Cached repeat call returns equal instant
        Date cachedRepeat = DateTimeUtils.parseDate("2025-04-10T08:15:30.123456Z");
        assertNotNull(cachedRepeat);
        assertEquals(isoMicros.getTime(), cachedRepeat.getTime());

        // Relative time accuracy
        SimpleDateFormat utcFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        utcFmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        String tenMinutesAgo = utcFmt.format(new Date(System.currentTimeMillis() - 10 * 60_000L));
        String formatted = DateTimeUtils.formatRelativeTime(tenMinutesAgo);
        assertTrue("Expected minutes relative string, got: " + formatted,
                formatted.contains("phút trước") || formatted.equals("Vừa xong"));
    }

    @Test
    public void testHighRefreshRateModeSelection() {
        int[] ids = {1, 2, 3, 4};
        int[] widths = {1080, 1080, 1080, 1440};
        int[] heights = {2400, 2400, 2400, 3200};
        float[] rates = {60.0f, 90.0f, 120.0f, 144.0f};

        // Selects highest refresh rate matching 1080x2400 (ignores 144Hz at 1440x3200)
        assertEquals(3, HighRefreshRateHelper.selectHighestRefreshModeId(1080, 2400, ids, widths, heights, rates));

        // Only 60Hz modes -> returns 0 (keep default)
        assertEquals(0, HighRefreshRateHelper.selectHighestRefreshModeId(
                1080, 2400,
                new int[]{1}, new int[]{1080}, new int[]{2400}, new float[]{60.0f}));

        // Slightly above 60Hz (e.g. 90Hz) -> selects it
        assertEquals(2, HighRefreshRateHelper.selectHighestRefreshModeId(
                1080, 2400,
                new int[]{1, 2}, new int[]{1080, 1080}, new int[]{2400, 2400}, new float[]{60.0f, 90.0f}));
    }
}
