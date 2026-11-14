package org.telegram.SQLite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;

public class NanogramStatementCacheTest {

    private final ArrayList<String> disposed = new ArrayList<>();

    private NanogramStatementCache<String> cache(int size) {
        return new NanogramStatementCache<>(size, disposed::add);
    }

    @Test
    public void reusesIdleStatementAfterRelease() {
        NanogramStatementCache<String> cache = cache(4);
        String stmt = "stmt-a";
        assertNull(cache.acquire("a"));
        assertTrue(cache.offer("a", stmt));
        assertTrue(cache.release(stmt));
        assertSame(stmt, cache.acquire("a"));
    }

    @Test
    public void statementInUseIsNotHandedOutTwice() {
        NanogramStatementCache<String> cache = cache(4);
        cache.offer("a", "stmt-a");
        assertNull(cache.acquire("a"));
        assertFalse(cache.offer("a", "other"));
    }

    @Test
    public void evictsLeastRecentlyUsedIdleEntry() {
        NanogramStatementCache<String> cache = cache(2);
        cache.offer("a", "A");
        cache.release("A");
        cache.offer("b", "B");
        cache.release("B");
        cache.acquire("a");
        cache.release("A");
        assertTrue(cache.offer("c", "C"));
        assertEquals(1, disposed.size());
        assertEquals("B", disposed.get(0));
        assertEquals(2, cache.size());
    }

    @Test
    public void doesNotEvictStatementsInUse() {
        NanogramStatementCache<String> cache = cache(1);
        cache.offer("a", "A");
        assertFalse(cache.offer("b", "B"));
        assertTrue(disposed.isEmpty());
    }

    @Test
    public void releaseOfUnknownStatementReportsNotKept() {
        NanogramStatementCache<String> cache = cache(1);
        assertFalse(cache.release("never-offered"));
    }

    @Test
    public void clearDisposesEverything() {
        NanogramStatementCache<String> cache = cache(4);
        cache.offer("a", "A");
        cache.offer("b", "B");
        cache.release("B");
        cache.clear();
        assertEquals(2, disposed.size());
        assertEquals(0, cache.size());
    }
}
