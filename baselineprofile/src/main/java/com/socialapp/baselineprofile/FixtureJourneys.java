package com.socialapp.baselineprofile;

import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.benchmark.macro.MacrobenchmarkScope;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import java.io.IOException;
import java.util.regex.Pattern;

import kotlin.Unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

final class FixtureJourneys {
    private static final String PACKAGE = "com.socialapp.profile";
    private static final String POST_TITLE = "Bài viết thử nghiệm số 1 - Cập nhật tin tức mới nhất trong ngày";
    private static final String POST_BODY = "Nội dung chi tiết bài viết số 1 từ server giả lập.";
    private static final String FIRST_COMMENT = "Bình luận giả lập số 1 phục vụ kiểm thử prefetch.";
    private static final long TIMEOUT_MS = 15_000;

    private FixtureJourneys() {}

    static String targetPackage() {
        String packageName = InstrumentationRegistry.getArguments().getString("targetAppId", PACKAGE);
        assertEquals("Expected the isolated fixture APK", PACKAGE, packageName);
        return packageName;
    }

    static UiObject2 openNewestFeed(MacrobenchmarkScope scope) {
        scope.startActivityAndWait(intent -> Unit.INSTANCE);
        UiDevice device = scope.getDevice();
        requireVisible(device, resource("feed_tab_layout")
                .hasDescendant(By.selected(true).desc("Mới nhất")), "Selected Newest tab");
        requireVisible(device, resource("feed_recycler_view"), "Feed Newest list");
        return requireVisible(device, resource("post_title").text(POST_TITLE), "Fixture post on Feed Newest");
    }

    static void openFixturePost(MacrobenchmarkScope scope) {
        UiObject2 fixture = openNewestFeed(scope);
        long requestedAt = SystemClock.elapsedRealtime();
        clickAccessible(fixture, POST_TITLE);
        UiDevice device = scope.getDevice();
        BySelector detail = resource("post_detail_title").text(POST_TITLE);
        if (!device.wait(Until.hasObject(detail), 2_000)) {
            // Repeat a dropped action only if the same source card is still visible.
            UiObject2 stillOnFeed = device.findObject(resource("post_title").text(POST_TITLE));
            if (stillOnFeed != null) clickAccessible(stillOnFeed, POST_TITLE);
            assertNotNull("Fixture Post Detail title", device.wait(Until.findObject(detail),
                    Math.max(0, requestedAt + TIMEOUT_MS - SystemClock.elapsedRealtime())));
        }
        waitForPostVisualState(device, requestedAt);
    }

    private static void waitForPostVisualState(UiDevice device, long requestedAt) {
        // The app emits this after WebView's visual-state callback on the resumed Post Detail.
        // Virtual text nodes in WebView can be absent even after this callback.
        String command = "tail -n 128 /sdcard/Android/data/" + PACKAGE
                + "/files/metrics/performance_v1.csv";
        long deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS;
        do {
            try {
                for (String row : device.executeShellCommand(command).split("\n")) {
                    String[] fields = row.split(",", 17);
                    if (fields.length == 17 && "1".equals(fields[0])
                            && "NAVIGATION_CONTENT_READY".equals(fields[2]) && !fields[7].isEmpty()
                            && "LIVE".equals(fields[9]) && "POST_DETAIL".equals(fields[10])
                            && Long.parseLong(fields[14]) >= requestedAt
                            && Long.parseLong(fields[15]) <= SystemClock.elapsedRealtime()) return;
                }
            } catch (IOException error) {
                throw new AssertionError("Could not read fixture visual-state telemetry", error);
            }
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("Timed out waiting for fixture article visual state");
    }

    static void openFixtureComments(MacrobenchmarkScope scope) {
        openFixturePost(scope);
        UiDevice device = scope.getDevice();
        BySelector selector = resource("btn_comments");
        UiObject2 button = device.findObject(selector);
        if (button == null) {
            UiObject2 article = requireVisible(device, resource("post_detail_content_container"), "Post Detail content");
            for (int scroll = 0; scroll < 3 && button == null; scroll++) {
                article.scroll(Direction.DOWN, 0.6f);
                button = device.findObject(selector);
            }
        }
        assertNotNull("Comments button for fixture post 1", button);
        clickAccessible(button, null);
        waitForComments(device);
    }

    static void openSearch(MacrobenchmarkScope scope) {
        openNewestFeed(scope);
        UiDevice device = scope.getDevice();
        bottomNavButton(device, "nav_search").click();
        waitForSearch(device);
    }

    static void waitForSearch(UiDevice device) {
        requireVisible(device, resource("nav_search").selected(true), "Selected Search destination");
        requireVisible(device, resource("search_input_edit_text"), "Search input");
        requireVisible(device, resource("btn_search_submit"), "Search action");
    }

    static void openFactCheck(MacrobenchmarkScope scope) {
        openNewestFeed(scope);
        UiDevice device = scope.getDevice();
        bottomNavButton(device, "nav_factcheck").click();
        waitForFactCheck(device);
    }

    static UiObject2 bottomNavButton(UiDevice device, String id) {
        return requireVisible(device, resource(id), "Bottom navigation " + id);
    }

    private static void clickAccessible(UiObject2 object, String text) {
        AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().getRootInActiveWindow();
        assertNotNull("Missing active accessibility window", root);
        String resourceName = object.getResourceName();
        for (AccessibilityNodeInfo match : root.findAccessibilityNodeInfosByViewId(resourceName)) {
            if (!match.isVisibleToUser() || (text != null && !text.equals(String.valueOf(match.getText())))) continue;
            AccessibilityNodeInfo clickable = match;
            while (clickable != null && !clickable.isClickable()) {
                clickable = clickable.getParent();
            }
            assertNotNull("Missing clickable node for " + resourceName, clickable);
            assertTrue("Accessibility click rejected for " + resourceName, clickable.refresh()
                    && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK));
            return;
        }
        throw new AssertionError("No matching accessibility node for " + resourceName);
    }

    static void waitForFactCheck(UiDevice device) {
        requireVisible(device, resource("nav_factcheck").selected(true), "Selected Fact-Check destination");
        requireVisible(device, resource("btnGoToDetection"), "Fact-Check detection entry");
        requireVisible(device, resource("btnGoToHistory"), "Fact-Check history entry");
    }

    static void waitForPostDetail(UiDevice device) {
        requireVisible(device, resource("post_detail_title").text(POST_TITLE), "Fixture Post Detail title");
        requireVisible(device, By.pkg(PACKAGE).text(POST_BODY), "Rendered fixture article body");
    }

    static UiObject2 findCommentsButton(UiDevice device) {
        UiObject2 content = requireVisible(device, resource("post_detail_content_container"), "Post Detail content");
        UiObject2 comments = content.scrollUntil(Direction.DOWN, Until.findObject(resource("btn_comments")));
        assertNotNull("Comments button for fixture post 1", comments);
        return comments;
    }

    static void waitForComments(UiDevice device) {
        requireVisible(device, resource("comment_guest_prompt"), "Comments guest state");
        BySelector ready = By.res(Pattern.compile(
                ".+:id/(comments_recycler_view|comments_empty_state)")).pkg(PACKAGE);
        UiObject2 result = requireVisible(device, ready, "Comments data or empty state");
        if (result.getResourceName().endsWith(":id/comments_recycler_view")) {
            requireVisible(device, resource("comment_content_text").text(FIRST_COMMENT), "Fixture comment");
        }
    }

    private static BySelector resource(String name) {
        return By.res(Pattern.compile(".+:id/" + name)).pkg(PACKAGE);
    }

    private static UiObject2 requireVisible(UiDevice device, BySelector selector, String description) {
        UiObject2 object = device.wait(Until.findObject(selector), TIMEOUT_MS);
        assertNotNull("Timed out waiting for " + description, object);
        return object;
    }
}
