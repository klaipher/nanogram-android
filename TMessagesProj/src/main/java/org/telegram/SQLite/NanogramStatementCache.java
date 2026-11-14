package org.telegram.SQLite;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class NanogramStatementCache<T> {

    public interface Disposer<T> {
        void dispose(T value);
    }

    private static class Entry<T> {
        final T value;
        boolean inUse = true;

        Entry(T value) {
            this.value = value;
        }
    }

    private final int maxSize;
    private final Disposer<T> disposer;
    private final LinkedHashMap<String, Entry<T>> entries = new LinkedHashMap<>(16, 0.75f, true);

    public NanogramStatementCache(int maxSize, Disposer<T> disposer) {
        this.maxSize = maxSize;
        this.disposer = disposer;
    }

    public synchronized T acquire(String sql) {
        Entry<T> entry = entries.get(sql);
        if (entry == null || entry.inUse) {
            return null;
        }
        entry.inUse = true;
        return entry.value;
    }

    public synchronized boolean offer(String sql, T value) {
        if (entries.containsKey(sql)) {
            return false;
        }
        if (entries.size() >= maxSize && !evictOneIdle()) {
            return false;
        }
        entries.put(sql, new Entry<>(value));
        return true;
    }

    public synchronized boolean release(T value) {
        for (Entry<T> entry : entries.values()) {
            if (entry.value == value) {
                entry.inUse = false;
                return true;
            }
        }
        return false;
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized void clear() {
        ArrayList<Entry<T>> all = new ArrayList<>(entries.values());
        entries.clear();
        for (Entry<T> entry : all) {
            disposer.dispose(entry.value);
        }
    }

    private boolean evictOneIdle() {
        Iterator<Map.Entry<String, Entry<T>>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Entry<T> entry = it.next().getValue();
            if (!entry.inUse) {
                it.remove();
                disposer.dispose(entry.value);
                return true;
            }
        }
        return false;
    }
}
