package com.milezerosoftware.mc.screenshotmanagerenhanced.client.render;

import com.milezerosoftware.mc.screenshotmanagerenhanced.client.render.ThumbnailLruCache.CacheKey;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ThumbnailLruCache}.
 */
public class ThumbnailLruCacheTest {

    @Test
    void testLruEvictionOrder() {
        List<String> evicted = new ArrayList<>();
        ThumbnailLruCache<String> cache = new ThumbnailLruCache<>(3, evicted::add);

        CacheKey k1 = CacheKey.of(Path.of("a.png"), 320);
        CacheKey k2 = CacheKey.of(Path.of("b.png"), 320);
        CacheKey k3 = CacheKey.of(Path.of("c.png"), 320);
        CacheKey k4 = CacheKey.of(Path.of("d.png"), 320);

        cache.put(k1, "v1");
        cache.put(k2, "v2");
        cache.put(k3, "v3");
        assertTrue(evicted.isEmpty());
        assertEquals(3, cache.size());

        // Inserting k4 should evict eldest (k1)
        cache.put(k4, "v4");
        assertEquals(3, cache.size());
        assertEquals(List.of("v1"), evicted);
        assertFalse(cache.containsKey(k1));
        assertTrue(cache.containsKey(k2));
        assertTrue(cache.containsKey(k3));
        assertTrue(cache.containsKey(k4));
    }

    @Test
    void testAccessPromotesEntryToMostRecentlyUsed() {
        List<String> evicted = new ArrayList<>();
        ThumbnailLruCache<String> cache = new ThumbnailLruCache<>(3, evicted::add);

        CacheKey k1 = CacheKey.of(Path.of("a.png"), 320);
        CacheKey k2 = CacheKey.of(Path.of("b.png"), 320);
        CacheKey k3 = CacheKey.of(Path.of("c.png"), 320);
        CacheKey k4 = CacheKey.of(Path.of("d.png"), 320);

        cache.put(k1, "v1");
        cache.put(k2, "v2");
        cache.put(k3, "v3");

        // Access k1 so it becomes MRU; k2 is now LRU
        assertEquals("v1", cache.get(k1));

        cache.put(k4, "v4");
        assertEquals(List.of("v2"), evicted);
        assertTrue(cache.containsKey(k1));
        assertFalse(cache.containsKey(k2));
        assertTrue(cache.containsKey(k3));
        assertTrue(cache.containsKey(k4));
    }

    @Test
    void testContainsKeyDoesNotPromoteEntry() {
        List<String> evicted = new ArrayList<>();
        ThumbnailLruCache<String> cache = new ThumbnailLruCache<>(2, evicted::add);

        CacheKey k1 = CacheKey.of(Path.of("a.png"), 320);
        CacheKey k2 = CacheKey.of(Path.of("b.png"), 320);
        CacheKey k3 = CacheKey.of(Path.of("c.png"), 320);

        cache.put(k1, "v1");
        cache.put(k2, "v2");
        assertTrue(cache.containsKey(k1));

        // k1 is still LRU because containsKey does not promote
        cache.put(k3, "v3");
        assertEquals(List.of("v1"), evicted);
        assertFalse(cache.containsKey(k1));
    }

    @Test
    void testOverwriteKeyEvictsPreviousValue() {
        List<String> evicted = new ArrayList<>();
        ThumbnailLruCache<String> cache = new ThumbnailLruCache<>(3, evicted::add);

        CacheKey k1 = CacheKey.of(Path.of("a.png"), 320);
        cache.put(k1, "old");
        String prev = cache.put(k1, "new");

        assertEquals("old", prev);
        assertEquals("new", cache.get(k1));
        assertEquals(1, cache.size());
        assertEquals(List.of("old"), evicted);
    }

    @Test
    void testEvictByPathRemovesAllTargetWidthsForPath() {
        List<String> evicted = new ArrayList<>();
        ThumbnailLruCache<String> cache = new ThumbnailLruCache<>(10, evicted::add);

        Path target = Path.of("/screenshots/world/img1.png");
        Path other = Path.of("/screenshots/world/img2.png");

        cache.put(CacheKey.of(target, 320), "thumb-320");
        cache.put(CacheKey.of(target, 1920), "full-1920");
        cache.put(CacheKey.of(other, 320), "other-320");

        int count = cache.evictByPath(Path.of("/screenshots/world/sub/../img1.png"));
        assertEquals(2, count);
        assertEquals(1, cache.size());
        assertTrue(evicted.contains("thumb-320"));
        assertTrue(evicted.contains("full-1920"));
        assertEquals("other-320", cache.get(other, 320));
    }

    @Test
    void testEvictByPathNullAndMissingAreNoOps() {
        List<String> evicted = new ArrayList<>();
        ThumbnailLruCache<String> cache = new ThumbnailLruCache<>(5, evicted::add);
        cache.put(CacheKey.of(Path.of("a.png"), 320), "v1");

        assertEquals(0, cache.evictByPath(null));
        assertEquals(0, cache.evictByPath(Path.of("nonexistent.png")));
        assertEquals(1, cache.size());
        assertTrue(evicted.isEmpty());
    }

    @Test
    void testRemoveInvokesEvictionCallback() {
        List<String> evicted = new ArrayList<>();
        ThumbnailLruCache<String> cache = new ThumbnailLruCache<>(5, evicted::add);

        CacheKey k1 = CacheKey.of(Path.of("a.png"), 320);
        cache.put(k1, "v1");

        assertEquals("v1", cache.remove(k1));
        assertNull(cache.remove(k1));
        assertEquals(List.of("v1"), evicted);
        assertTrue(cache.isEmpty());
    }

    @Test
    void testClearEvictsAllEntriesAndEmptiesCache() {
        List<String> evicted = new ArrayList<>();
        ThumbnailLruCache<String> cache = new ThumbnailLruCache<>(5, evicted::add);

        cache.put(CacheKey.of(Path.of("a.png"), 320), "v1");
        cache.put(CacheKey.of(Path.of("b.png"), 320), "v2");
        cache.clear();

        assertTrue(cache.isEmpty());
        assertEquals(0, cache.size());
        assertEquals(2, evicted.size());
        assertTrue(evicted.contains("v1"));
        assertTrue(evicted.contains("v2"));
    }

    @Test
    void testInvalidCapacityAndKeyValidation() {
        assertThrows(IllegalArgumentException.class, () -> new ThumbnailLruCache<String>(0, null));
        assertThrows(IllegalArgumentException.class, () -> new ThumbnailLruCache<String>(-5, null));
        assertThrows(NullPointerException.class, () -> CacheKey.of(null, 320));
        assertThrows(IllegalArgumentException.class, () -> CacheKey.of(Path.of("a.png"), 0));
        assertThrows(IllegalArgumentException.class, () -> CacheKey.of(Path.of("a.png"), -1));
    }

    @Test
    void testNullEvictionCallbackIsSafe() {
        ThumbnailLruCache<String> cache = new ThumbnailLruCache<>(1, null);
        cache.put(CacheKey.of(Path.of("a.png"), 320), "v1");
        cache.put(CacheKey.of(Path.of("b.png"), 320), "v2");
        assertEquals(1, cache.size());
        assertEquals("v2", cache.get(Path.of("b.png"), 320));
        cache.clear();
        assertTrue(cache.isEmpty());
    }
}
