package org.telegram.messenger.nanogram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NanogramLinkHygieneTest {

    /** Gradle runs unit tests in the module directory, so the shared submodule is one level up. */
    static File sharedData(String name) {
        for (String root : new String[]{"../shared/data", "shared/data"}) {
            final File file = new File(root, name);
            if (file.isFile()) {
                return file;
            }
        }
        throw new AssertionError("shared/data/" + name + " not found: initialise the shared submodule (nanogram-deps)");
    }

    static String read(String name) throws Exception {
        return new String(Files.readAllBytes(sharedData(name).toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void rulesMatchTheSharedList() throws Exception {
        final List<String> shared = new ArrayList<>();
        for (String line : read("link-tracking-params.txt").split("\\R")) {
            final String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                shared.add(trimmed);
            }
        }
        assertEquals(shared, Arrays.asList(NanogramLinkHygiene.RULES));
    }

    @Test
    public void stripVectorsFromTheSharedData() throws Exception {
        final JsonArray strip = JsonParser.parseString(read("link-hygiene-vectors.json")).getAsJsonObject().getAsJsonArray("strip");
        assertTrue(strip.size() > 10);
        for (JsonElement element : strip) {
            final JsonObject vector = element.getAsJsonObject();
            final String input = vector.get("input").getAsString();
            assertEquals(input, vector.get("output").getAsString(), NanogramLinkHygiene.clean(input));
        }
    }

    @Test
    public void telegramVectorsFromTheSharedData() throws Exception {
        final JsonArray telegram = JsonParser.parseString(read("link-hygiene-vectors.json")).getAsJsonObject().getAsJsonArray("telegram");
        assertTrue(telegram.size() > 5);
        for (JsonElement element : telegram) {
            final JsonObject vector = element.getAsJsonObject();
            final String input = vector.get("input").getAsString();
            assertEquals(input, vector.get("telegram").getAsBoolean(), NanogramLinkHygiene.isTelegramLink(input));
        }
    }

    @Test
    public void stripsTrackingKeepsTheRest() {
        assertEquals("https://example.com/a?id=5&x=1#top",
                NanogramLinkHygiene.clean("https://example.com/a?utm_source=x&id=5&fbclid=abc&x=1#top"));
    }

    @Test
    public void dropsQuestionMarkWhenNothingLeft() {
        assertEquals("https://example.com/a", NanogramLinkHygiene.clean("https://example.com/a?utm_medium=m&gclid=1"));
        assertEquals("https://example.com/a#f", NanogramLinkHygiene.clean("https://example.com/a?igshid=1#f"));
    }

    @Test
    public void untouchedLinksAreReturnedAsIs() {
        final String url = "https://example.com/a?q=utm_source&b=2";
        assertTrue(url == NanogramLinkHygiene.clean(url));
        assertEquals("https://example.com/", NanogramLinkHygiene.clean("https://example.com/"));
    }

    @Test
    public void prefixesAndCase() {
        assertEquals("https://e.com/?a=1", NanogramLinkHygiene.clean("https://e.com/?UTM_Campaign=x&a=1"));
    }

    @Test
    public void siOnlyOnShareSites() {
        assertEquals("https://youtu.be/abc", NanogramLinkHygiene.clean("https://youtu.be/abc?si=XYZ"));
        assertEquals("https://open.spotify.com/track/1", NanogramLinkHygiene.clean("https://open.spotify.com/track/1?si=q"));
        assertEquals("https://shop.example.com/?si=5", NanogramLinkHygiene.clean("https://shop.example.com/?si=5"));
    }

    @Test
    public void otherSchemesAreNeverChanged() {
        assertEquals("tg://resolve?domain=a&utm_source=1", NanogramLinkHygiene.clean("tg://resolve?domain=a&utm_source=1"));
        assertEquals("mailto:a@b.c?utm_source=1", NanogramLinkHygiene.clean("mailto:a@b.c?utm_source=1"));
        assertNull(NanogramLinkHygiene.clean(null));
    }

    @Test
    public void userInfoAndPortDoNotConfuseTheHost() {
        assertEquals("https://t.me:443/x", NanogramLinkHygiene.clean("https://t.me:443/x?utm_source=1"));
        assertEquals("https://u@example.com/a", NanogramLinkHygiene.clean("https://u@example.com/a?utm_source=1"));
    }

    @Test
    public void keepsEmptyAndValuelessParts() {
        assertEquals("https://e.com/?a&b=", NanogramLinkHygiene.clean("https://e.com/?a&utm_x=1&b="));
        assertEquals("https://e.com/?&&k=1", NanogramLinkHygiene.clean("https://e.com/?&utm_source=x&&k=1"));
    }

    @Test
    public void sharedListHostEntriesCoverSubdomains() {
        assertEquals("https://www.youtube.com/watch?v=1", NanogramLinkHygiene.clean("https://www.youtube.com/watch?v=1&feature=share&pp=x&si=q"));
        assertEquals("https://www.amazon.com/dp/B0?a=1", NanogramLinkHygiene.clean("https://www.amazon.com/dp/B0?tag=x&ref=y&pd_rd_w=1&pf_rd_r=2&a=1"));
        assertEquals("https://vm.tiktok.com/x", NanogramLinkHygiene.clean("https://vm.tiktok.com/x?share_id=1&is_copy_url=1&is_from_webapp=1&sender_device=pc"));
        assertEquals("https://m.facebook.com/p?id=1", NanogramLinkHygiene.clean("https://m.facebook.com/p?refsrc=a&__tn__=b&__cft__[0]=c&id=1"));
        assertEquals("https://notamazon.com/?tag=x&ref=y", NanogramLinkHygiene.clean("https://notamazon.com/?tag=x&ref=y"));
        assertEquals("https://example.com/?feature=a&pp=b", NanogramLinkHygiene.clean("https://example.com/?feature=a&pp=b"));
        assertEquals("https://x.com/a?si=1", NanogramLinkHygiene.clean("https://x.com/a?si=1"));
    }

    @Test
    public void sharedListGlobalNames() {
        assertEquals("https://e.com/?a=1", NanogramLinkHygiene.clean("https://e.com/?__hssc=1&__hstc=2&__hsfp=3&hsctatracking=4&vero_conv=5&spm=6&scm=7&a=1"));
    }

    @Test
    public void detectsTelegramLinks() {
        assertTrue(NanogramLinkHygiene.isTelegramLink("https://t.me/durov"));
        assertTrue(NanogramLinkHygiene.isTelegramLink("HTTP://T.ME/durov"));
        assertTrue(NanogramLinkHygiene.isTelegramLink("tg://join?invite=a"));
        assertTrue(NanogramLinkHygiene.isTelegramLink("https://www.t.me/a"));
        assertFalse(NanogramLinkHygiene.isTelegramLink("tg:resolve?domain=a"));
        assertFalse(NanogramLinkHygiene.isTelegramLink("https://telesco.pe/a"));
        assertTrue(NanogramLinkHygiene.isTelegramLink("https://telegram.me/a"));
        assertFalse(NanogramLinkHygiene.isTelegramLink("https://example.com/t.me"));
        assertFalse(NanogramLinkHygiene.isTelegramLink("https://nott.me/a"));
        assertFalse(NanogramLinkHygiene.isTelegramLink(null));
    }
}
