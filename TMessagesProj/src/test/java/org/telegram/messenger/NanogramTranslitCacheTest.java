package org.telegram.messenger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class NanogramTranslitCacheTest {

    @Test
    public void computesOncePerName() {
        NanogramTranslitCache cache = new NanogramTranslitCache(8);
        AtomicInteger calls = new AtomicInteger();
        assertEquals("ivan", cache.get("иван", s -> {
            calls.incrementAndGet();
            return "ivan";
        }));
        assertEquals("ivan", cache.get("иван", s -> {
            calls.incrementAndGet();
            return "other";
        }));
        assertEquals(1, calls.get());
    }

    @Test
    public void evictsLeastRecentlyUsed() {
        NanogramTranslitCache cache = new NanogramTranslitCache(2);
        AtomicInteger calls = new AtomicInteger();
        cache.get("a", s -> {
            calls.incrementAndGet();
            return "A";
        });
        cache.get("b", s -> "B");
        cache.get("a", s -> "A");
        cache.get("c", s -> "C");
        cache.get("b", s -> {
            calls.incrementAndGet();
            return "B";
        });
        assertEquals(2, calls.get());
    }

    @Test
    public void nullStaysNull() {
        NanogramTranslitCache cache = new NanogramTranslitCache(2);
        assertNull(cache.get(null, s -> "x"));
        assertNull(cache.get("a", s -> null));
    }

    @Test
    public void clearForgetsEntries() {
        NanogramTranslitCache cache = new NanogramTranslitCache(4);
        cache.get("a", s -> "A");
        cache.clear();
        assertEquals("B", cache.get("a", s -> "B"));
    }
}
