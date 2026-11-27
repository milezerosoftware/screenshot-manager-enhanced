package com.milezerosoftware.mc.screenshotmanagerenhanced.client.render;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Pure-Java bounded Least-Recently-Used (LRU) cache for decoded thumbnails.
 * <p>
 * Backed by an access-ordered {@link LinkedHashMap}. Whenever an entry is
 * evicted due to capacity, overwritten with a different value, explicitly
 * removed, or cleared, the configured eviction callback is invoked so callers
 * can release associated resources (such as GPU textures).
 *
 * @param <V> cached value type
 */
public class ThumbnailLruCache<V> {

    public static final int DEFAULT_MAX_ENTRIES = 96;

    /**
     * Normalized cache key pairing a screenshot file {@link Path} with its
     * requested target width.
     */
    public record CacheKey(Path path, int targetWidth) {
        public CacheKey {
            Objects.requireNonNull(path, "path must not be null");
            if (targetWidth <= 0) {
                throw new IllegalArgumentException("targetWidth must be positive, got: " + targetWidth);
            }
            path = path.normalize();
        }

        public static CacheKey of(Path path, int targetWidth) {
            return new CacheKey(path, targetWidth);
        }
    }

    private final int maxEntries;
    private final Consumer<V> onEvict;
    private final LinkedHashMap<CacheKey, V> entries;

    /**
     * Creates a bounded LRU cache.
     *
     * @param maxEntries maximum number of entries to retain (must be &gt; 0)
     * @param onEvict    optional callback invoked for every evicted, overwritten,
     *                   removed, or cleared value (may be {@code null})
     */
    public ThumbnailLruCache(int maxEntries, Consumer<V> onEvict) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive, got: " + maxEntries);
        }
        this.maxEntries = maxEntries;
        this.onEvict = onEvict != null ? onEvict : v -> {};
        this.entries = new LinkedHashMap<>(Math.min(maxEntries, 64), 0.75f, true);
    }

    /**
     * Retrieves the cached value and promotes it to most-recently-used.
     */
    public V get(CacheKey key) {
        Objects.requireNonNull(key, "key must not be null");
        synchronized (entries) {
            return entries.get(key);
        }
    }

    /**
     * Convenience overload to retrieve by {@link Path} and {@code targetWidth}.
     */
    public V get(Path path, int targetWidth) {
        return get(CacheKey.of(path, targetWidth));
    }

    /**
     * Checks whether the cache contains the key without affecting LRU order.
     */
    public boolean containsKey(CacheKey key) {
        Objects.requireNonNull(key, "key must not be null");
        synchronized (entries) {
            return entries.containsKey(key);
        }
    }

    /**
     * Inserts or updates an entry, promoting it to most-recently-used.
     * <p>
     * If the key previously mapped to a different value instance, the previous
     * value is passed to the eviction callback. If inserting causes the cache
     * to exceed {@link #maxEntries()}, the least-recently-used entries are
     * evicted and passed to the eviction callback.
     *
     * @return the previous value associated with {@code key}, or {@code null}
     */
    public V put(CacheKey key, V value) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");

        V previous;
        List<V> evicted = null;
        synchronized (entries) {
            previous = entries.put(key, value);
            if (previous != null && previous != value) {
                evicted = new ArrayList<>(2);
                evicted.add(previous);
            }
            while (entries.size() > maxEntries) {
                Iterator<Map.Entry<CacheKey, V>> it = entries.entrySet().iterator();
                Map.Entry<CacheKey, V> eldest = it.next();
                it.remove();
                if (evicted == null) {
                    evicted = new ArrayList<>(1);
                }
                evicted.add(eldest.getValue());
            }
        }

        if (evicted != null) {
            for (V v : evicted) {
                onEvict.accept(v);
            }
        }
        return previous;
    }

    /**
     * Removes a single entry by key and notifies the eviction callback if present.
     */
    public V remove(CacheKey key) {
        Objects.requireNonNull(key, "key must not be null");
        V removed;
        synchronized (entries) {
            removed = entries.remove(key);
        }
        if (removed != null) {
            onEvict.accept(removed);
        }
        return removed;
    }

    /**
     * Removes all cached entries associated with the given {@link Path} across
     * any {@code targetWidth}, invoking the eviction callback for each removed value.
     *
     * @return the number of entries evicted
     */
    public int evictByPath(Path path) {
        if (path == null) {
            return 0;
        }
        Path normalized = path.normalize();
        List<V> evicted = new ArrayList<>();
        synchronized (entries) {
            Iterator<Map.Entry<CacheKey, V>> it = entries.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<CacheKey, V> entry = it.next();
                if (entry.getKey().path().equals(normalized)) {
                    evicted.add(entry.getValue());
                    it.remove();
                }
            }
        }
        for (V v : evicted) {
            onEvict.accept(v);
        }
        return evicted.size();
    }

    /**
     * Removes all entries from the cache and invokes the eviction callback for each.
     */
    public void clear() {
        List<V> evicted;
        synchronized (entries) {
            if (entries.isEmpty()) {
                return;
            }
            evicted = new ArrayList<>(entries.values());
            entries.clear();
        }
        for (V v : evicted) {
            onEvict.accept(v);
        }
    }

    public int size() {
        synchronized (entries) {
            return entries.size();
        }
    }

    public boolean isEmpty() {
        synchronized (entries) {
            return entries.isEmpty();
        }
    }

    public int maxEntries() {
        return maxEntries;
    }
}
