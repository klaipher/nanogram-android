package org.telegram.messenger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public class NanogramTranslitCache {

    private static final int MAX_ENTRIES = 4096;
    private static final NanogramTranslitCache shared = new NanogramTranslitCache(MAX_ENTRIES);

    private final LinkedHashMap<String, String> map;

    public NanogramTranslitCache(int maxEntries) {
        map = new LinkedHashMap<String, String>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > maxEntries;
            }
        };
    }

    public static String translit(String src) {
        return shared.get(src, s -> LocaleController.getInstance().getTranslitString(s));
    }

    public static void clearShared() {
        shared.clear();
    }

    public synchronized void clear() {
        map.clear();
    }

    public String get(String src, Function<String, String> compute) {
        if (src == null) {
            return null;
        }
        synchronized (this) {
            String value = map.get(src);
            if (value != null) {
                return value;
            }
        }
        String value = compute.apply(src);
        if (value != null) {
            synchronized (this) {
                map.put(src, value);
            }
        }
        return value;
    }
}
