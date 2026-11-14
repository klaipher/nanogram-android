package org.telegram.messenger.camera;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.telegram.messenger.nanogram.NanogramFeatures;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class NanogramCameraExtensionsTest {

    private static final int AUTO = NanogramCameraExtensions.EXT_AUTOMATIC;
    private static final int HDR = NanogramCameraExtensions.EXT_HDR;
    private static final int NIGHT = NanogramCameraExtensions.EXT_NIGHT;

    @After
    public void clear() {
        NanogramCameraExtensions.clearBlacklist();
    }

    private static List<Integer> list(Integer... values) {
        return Arrays.asList(values);
    }

    private static List<int[]> sizes(int... wh) {
        List<int[]> result = new ArrayList<>();
        for (int i = 0; i < wh.length; i += 2) {
            result.add(new int[]{wh[i], wh[i + 1]});
        }
        return result;
    }

    @Test
    public void autoPrefersAutomaticThenHdrThenNight() {
        assertEquals(AUTO, NanogramCameraExtensions.pickExtension(list(NIGHT, HDR, AUTO), 0));
        assertEquals(HDR, NanogramCameraExtensions.pickExtension(list(NIGHT, HDR), 0));
        assertEquals(NIGHT, NanogramCameraExtensions.pickExtension(list(NIGHT), 0));
    }

    @Test
    public void hdrFallsBackToAutomaticOnly() {
        assertEquals(HDR, NanogramCameraExtensions.pickExtension(list(AUTO, HDR, NIGHT), 1));
        assertEquals(AUTO, NanogramCameraExtensions.pickExtension(list(AUTO, NIGHT), 1));
        assertEquals(-1, NanogramCameraExtensions.pickExtension(list(NIGHT), 1));
    }

    @Test
    public void nightFallsBackToAutomaticOnly() {
        assertEquals(NIGHT, NanogramCameraExtensions.pickExtension(list(AUTO, HDR, NIGHT), 2));
        assertEquals(AUTO, NanogramCameraExtensions.pickExtension(list(AUTO, HDR), 2));
        assertEquals(-1, NanogramCameraExtensions.pickExtension(list(HDR), 2));
    }

    @Test
    public void emptyOrNullListGivesNone() {
        for (int mode = 0; mode <= 2; mode++) {
            assertEquals(-1, NanogramCameraExtensions.pickExtension(Collections.emptyList(), mode));
            assertEquals(-1, NanogramCameraExtensions.pickExtension(null, mode));
        }
    }

    @Test
    public void unknownModeBehavesLikeAuto() {
        assertEquals(AUTO, NanogramCameraExtensions.pickExtension(list(AUTO, HDR, NIGHT), 7));
        assertEquals(HDR, NanogramCameraExtensions.pickExtension(list(HDR, NIGHT), -1));
    }

    @Test
    public void stillSizeKeepsAspectWithinThreePercent() {
        List<int[]> list = sizes(4000, 3000, 4032, 2268, 3264, 2448, 1920, 1080);
        assertArrayEquals(new int[]{4000, 3000}, NanogramCameraExtensions.pickStillSize(list, 4f / 3f, 12_000_000L));
        assertArrayEquals(new int[]{4032, 2268}, NanogramCameraExtensions.pickStillSize(list, 16f / 9f, 12_000_000L));
        assertArrayEquals(new int[]{3264, 2448}, NanogramCameraExtensions.pickStillSize(sizes(3264, 2448, 4160, 3120), 1.3333f, 10_000_000L));
    }

    @Test
    public void stillSizeCapsPixels() {
        List<int[]> list = sizes(8000, 6000, 4000, 3000, 2000, 1500);
        assertArrayEquals(new int[]{4000, 3000}, NanogramCameraExtensions.pickStillSize(list, 4f / 3f, 12_000_000L));
        assertArrayEquals(new int[]{2000, 1500}, NanogramCameraExtensions.pickStillSize(list, 4f / 3f, 3_000_000L));
        assertNull(NanogramCameraExtensions.pickStillSize(list, 4f / 3f, 1000L));
    }

    @Test
    public void stillSizeWithoutAspectMatchTakesLargestUnderCap() {
        List<int[]> list = sizes(4000, 3000, 3000, 3000);
        assertArrayEquals(new int[]{4000, 3000}, NanogramCameraExtensions.pickStillSize(list, 16f / 9f, 12_000_000L));
    }

    @Test
    public void stillSizeOutsideToleranceIsNotAnAspectMatch() {
        List<int[]> list = sizes(4000, 3000, 2000, 1000);
        assertArrayEquals(new int[]{4000, 3000}, NanogramCameraExtensions.pickStillSize(list, 2.2f, 12_000_000L));
        assertArrayEquals(new int[]{2000, 1000}, NanogramCameraExtensions.pickStillSize(list, 2.05f, 12_000_000L));
    }

    @Test
    public void stillSizeEmptyGivesNull() {
        assertNull(NanogramCameraExtensions.pickStillSize(Collections.emptyList(), 1.33f, 12_000_000L));
        assertNull(NanogramCameraExtensions.pickStillSize(null, 1.33f, 12_000_000L));
    }

    @Test
    public void stillSizeTiesPreferClosestAspectThenFirst() {
        // same area: 3000x4000 vs 4000x3000 for aspect 1.33; the landscape one is closer
        List<int[]> list = sizes(3000, 4000, 4000, 3000);
        assertArrayEquals(new int[]{4000, 3000}, NanogramCameraExtensions.pickStillSize(list, 4f / 3f, 12_000_000L));
        int[] first = {4000, 3000};
        int[] second = {4000, 3000};
        assertTrue(first == NanogramCameraExtensions.pickStillSize(Arrays.asList(first, second), 4f / 3f, 12_000_000L));
    }

    // Reference copy of Camera2Session.chooseOptimalSize with notBigger = false.
    private static int[] reference(List<int[]> choices, int width, int height) {
        List<int[]> aspect = new ArrayList<>();
        List<int[]> big = new ArrayList<>();
        for (int[] o : choices) {
            if (o[1] == o[0] * height / width && o[0] >= width && o[1] >= height) {
                aspect.add(o);
            } else if (o[1] * o[0] <= width * height * 4 && o[0] >= width && o[1] >= height) {
                big.add(o);
            }
        }
        if (!aspect.isEmpty()) {
            return Collections.min(aspect, (a, b) -> Long.signum((long) a[0] * a[1] - (long) b[0] * b[1]));
        } else if (!big.isEmpty()) {
            return Collections.min(big, (a, b) -> Long.signum((long) a[0] * a[1] - (long) b[0] * b[1]));
        }
        return Collections.max(choices, (a, b) -> Long.signum((long) a[0] * a[1] - (long) b[0] * b[1]));
    }

    @Test
    public void previewSizeMatchesChooseOptimalSize() {
        List<int[]> set = sizes(4000, 3000, 1920, 1080, 1600, 1200, 1440, 1080, 1280, 720, 960, 720, 640, 480, 176, 144);
        int[][] views = {{1080, 1920}, {1080, 2400}, {720, 1280}, {1080, 810}, {1440, 1080}, {640, 480}, {5000, 5000}, {1280, 720}, {800, 600}};
        for (int[] v : views) {
            assertArrayEquals("view " + v[0] + "x" + v[1], reference(set, v[0], v[1]), NanogramCameraExtensions.pickPreviewSize(set, v[0], v[1]));
        }
    }

    @Test
    public void previewSizeEmptyGivesNull() {
        assertNull(NanogramCameraExtensions.pickPreviewSize(Collections.emptyList(), 1080, 1920));
        assertNull(NanogramCameraExtensions.pickPreviewSize(null, 1080, 1920));
    }

    @Test
    public void blacklistedPairIsNotPickedAgainButOthersAre() {
        List<Integer> supported = list(AUTO, HDR, NIGHT);
        assertEquals(AUTO, NanogramCameraExtensions.pickExtension(NanogramCameraExtensions.withoutBlacklisted("0", supported), 0));
        NanogramCameraExtensions.blacklist("0", AUTO);
        assertTrue(NanogramCameraExtensions.isBlacklisted("0", AUTO));
        assertEquals(HDR, NanogramCameraExtensions.pickExtension(NanogramCameraExtensions.withoutBlacklisted("0", supported), 0));
        assertEquals(AUTO, NanogramCameraExtensions.pickExtension(NanogramCameraExtensions.withoutBlacklisted("1", supported), 0));
        assertFalse(NanogramCameraExtensions.isBlacklisted("0", HDR));
        assertFalse(NanogramCameraExtensions.isBlacklisted("1", AUTO));
    }

    @Test
    public void blacklistingEveryExtensionLeavesNone() {
        List<Integer> supported = list(AUTO, NIGHT);
        NanogramCameraExtensions.blacklist("2", AUTO);
        NanogramCameraExtensions.blacklist("2", NIGHT);
        assertEquals(-1, NanogramCameraExtensions.pickExtension(NanogramCameraExtensions.withoutBlacklisted("2", supported), 0));
    }

    @Test
    public void modesListsOnlyWhatExists() {
        NanogramCameraExtensions.Support none = new NanogramCameraExtensions.Support();
        assertEquals(0, NanogramCameraExtensions.modesFor(Collections.singletonList(none)).length);
        NanogramCameraExtensions.Support back = new NanogramCameraExtensions.Support();
        back.extensions.addAll(list(AUTO, HDR, NIGHT));
        NanogramCameraExtensions.Support front = new NanogramCameraExtensions.Support();
        front.extensions.add(AUTO);
        assertArrayEquals(new int[]{0, 1, 2}, NanogramCameraExtensions.modesFor(Arrays.asList(front, back)));
        assertArrayEquals(new int[]{0}, NanogramCameraExtensions.modesFor(Collections.singletonList(front)));
        NanogramCameraExtensions.Support onlyNight = new NanogramCameraExtensions.Support();
        onlyNight.extensions.add(NIGHT);
        assertArrayEquals(new int[]{0}, NanogramCameraExtensions.modesFor(Collections.singletonList(onlyNight)));
    }

    @Test
    public void featureDefaults() {
        assertFalse(NanogramFeatures.enhancedPhotos());
        assertEquals(0, NanogramFeatures.enhancedPhotoMode());
    }
}
