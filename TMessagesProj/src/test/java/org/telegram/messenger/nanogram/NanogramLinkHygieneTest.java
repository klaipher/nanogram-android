package org.telegram.messenger.nanogram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NanogramLinkHygieneTest {

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
        assertEquals("https://e.com/?a=1", NanogramLinkHygiene.clean("https://e.com/?UTM_Campaign=x&a=1&hsa_net=y"));
    }

    @Test
    public void siOnlyOnShareSites() {
        assertEquals("https://youtu.be/abc", NanogramLinkHygiene.clean("https://youtu.be/abc?si=XYZ"));
        assertEquals("https://open.spotify.com/track/1", NanogramLinkHygiene.clean("https://open.spotify.com/track/1?si=q"));
        assertEquals("https://shop.example.com/?si=5", NanogramLinkHygiene.clean("https://shop.example.com/?si=5"));
    }

    @Test
    public void telegramAndOtherSchemesAreNeverChanged() {
        assertEquals("https://t.me/bot?start=utm_x&utm_source=1", NanogramLinkHygiene.clean("https://t.me/bot?start=utm_x&utm_source=1"));
        assertEquals("tg://resolve?domain=a&utm_source=1", NanogramLinkHygiene.clean("tg://resolve?domain=a&utm_source=1"));
        assertEquals("mailto:a@b.c?utm_source=1", NanogramLinkHygiene.clean("mailto:a@b.c?utm_source=1"));
        assertNull(NanogramLinkHygiene.clean(null));
    }

    @Test
    public void userInfoAndPortDoNotConfuseTheHost() {
        assertEquals("https://t.me:443/x?utm_source=1", NanogramLinkHygiene.clean("https://t.me:443/x?utm_source=1"));
        assertEquals("https://u@example.com/a", NanogramLinkHygiene.clean("https://u@example.com/a?utm_source=1"));
    }

    @Test
    public void keepsEmptyAndValuelessParts() {
        assertEquals("https://e.com/?a&b=", NanogramLinkHygiene.clean("https://e.com/?a&utm_x=1&b="));
    }

    @Test
    public void detectsTelegramLinks() {
        assertTrue(NanogramLinkHygiene.isTelegramLink("https://t.me/durov"));
        assertTrue(NanogramLinkHygiene.isTelegramLink("HTTP://T.ME/durov"));
        assertTrue(NanogramLinkHygiene.isTelegramLink("tg://join?invite=a"));
        assertTrue(NanogramLinkHygiene.isTelegramLink("tg:resolve?domain=a"));
        assertTrue(NanogramLinkHygiene.isTelegramLink("https://telegram.me/a"));
        assertFalse(NanogramLinkHygiene.isTelegramLink("https://example.com/t.me"));
        assertFalse(NanogramLinkHygiene.isTelegramLink("https://nott.me/a"));
        assertFalse(NanogramLinkHygiene.isTelegramLink(null));
    }
}
