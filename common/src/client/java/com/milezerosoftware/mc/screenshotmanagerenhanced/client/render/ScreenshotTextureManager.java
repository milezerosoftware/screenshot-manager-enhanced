package com.milezerosoftware.mc.screenshotmanagerenhanced.client.render;

import com.milezerosoftware.mc.screenshotmanagerenhanced.client.render.ThumbnailLruCache.CacheKey;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Manages asynchronous thumbnail decoding, bounded LRU GPU texture caching,
 * frame-budgeted texture uploads, and generation-based cancellation.
 */
public class ScreenshotTextureManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("screenshot-manager-enhanced");

    public record ThumbnailResult(Identifier id, int width, int height) {
    }

    private static final Identifier PLACEHOLDER_ID = Identifier.fromNamespaceAndPath(
            "screenshot-manager-enhanced", "thumb/placeholder");
    private static volatile boolean placeholderRegistered = false;

    private static final ThumbnailLruCache<ThumbnailResult> CACHE = new ThumbnailLruCache<>(
            ThumbnailLruCache.DEFAULT_MAX_ENTRIES,
            result -> releaseTexture(result.id()));

    private static final AtomicLong GENERATION = new AtomicLong(0);
    private static final AtomicLong TEXTURE_COUNTER = new AtomicLong(0);

    private static final Object IN_FLIGHT_LOCK = new Object();
    private static final Map<CacheKey, List<Consumer<ThumbnailResult>>> IN_FLIGHT = new HashMap<>();

    private static final ConcurrentLinkedQueue<DecodedThumbnail> PENDING_UPLOADS = new ConcurrentLinkedQueue<>();

    private static final int LOADER_THREADS = Math.max(2,
            Math.min(3, Runtime.getRuntime().availableProcessors() / 2));

    private static final ExecutorService LOADER_EXECUTOR = Executors.newFixedThreadPool(
            LOADER_THREADS,
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(0);

                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "ScreenshotThumbLoader-" + counter.incrementAndGet());
                    t.setDaemon(true);
                    t.setPriority(Thread.MIN_PRIORITY);
                    return t;
                }
            });

    /**
     * Cached reflective resolution of the {@link DynamicTexture} constructor
     * across supported Minecraft versions (1.21.1-1.21.4 vs 1.21.5+ / 26.x).
     */
    private static final class DynamicTextureFactory {
        private static final Constructor<?> CONSTRUCTOR;
        private static final boolean TAKES_SUPPLIER;

        static {
            Constructor<?> resolved = null;
            boolean supplierFirst = false;
            Class<?> textureClass = DynamicTexture.class;

            // Try (NativeImage) - 1.20, 1.21.1-1.21.4
            try {
                resolved = textureClass.getConstructor(NativeImage.class);
            } catch (NoSuchMethodException ignored) {
                // Fall through to inspect other constructors
            }

            // Try (Supplier, NativeImage) - 1.21.5+ / 26.x
            if (resolved == null) {
                for (Constructor<?> c : textureClass.getConstructors()) {
                    Class<?>[] types = c.getParameterTypes();
                    if (types.length == 2
                            && types[1] == NativeImage.class
                            && Supplier.class.isAssignableFrom(types[0])) {
                        resolved = c;
                        supplierFirst = true;
                        break;
                    }
                }
            }

            if (resolved == null) {
                LOGGER.error("Could not find a suitable DynamicTexture constructor!");
            }

            CONSTRUCTOR = resolved;
            TAKES_SUPPLIER = supplierFirst;
        }

        static DynamicTexture create(Identifier id, NativeImage image) throws ReflectiveOperationException {
            if (CONSTRUCTOR == null) {
                return null;
            }
            if (TAKES_SUPPLIER) {
                Supplier<String> label = () -> "dynamic_" + id.getPath();
                return (DynamicTexture) CONSTRUCTOR.newInstance(label, image);
            }
            return (DynamicTexture) CONSTRUCTOR.newInstance(image);
        }
    }

    private static final class DecodedThumbnail {
        final CacheKey key;
        final NativeImage image;
        final int width;
        final int height;
        final long generation;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        DecodedThumbnail(CacheKey key, NativeImage image, int width, int height, long generation) {
            this.key = key;
            this.image = image;
            this.width = width;
            this.height = height;
            this.generation = generation;
        }

        void closeQuietly() {
            if (closed.compareAndSet(false, true)) {
                try {
                    image.close();
                } catch (Exception e) {
                    LOGGER.debug("Failed to close decoded NativeImage for {}", key.path(), e);
                }
            }
        }
    }

    /**
     * Returns a single lazily-created shared 1x1 transparent placeholder texture.
     * Must be called on the client render/main thread.
     */
    public static Identifier getPlaceholderId() {
        if (!placeholderRegistered) {
            NativeImage placeholder = new NativeImage(1, 1, true);
            if (registerTextureSafe(PLACEHOLDER_ID, placeholder)) {
                placeholderRegistered = true;
            } else {
                placeholder.close();
            }
        }
        return PLACEHOLDER_ID;
    }

    /**
     * Requests a thumbnail for {@code path} scaled to fit within {@code targetWidth}.
     * <p>
     * If already present in the LRU cache, {@code onLoaded} is invoked immediately.
     * Otherwise, an async decode is scheduled (coalescing duplicate in-flight
     * requests for the same key) and {@code onLoaded} is delivered on the main
     * thread during {@link #drainPendingUploads(int)}.
     */
    public static void requestThumbnail(Path path, int targetWidth, Consumer<ThumbnailResult> onLoaded) {
        if (path == null) {
            return;
        }
        int clampedWidth = Math.max(1, targetWidth);
        CacheKey key = CacheKey.of(path, clampedWidth);

        ThumbnailResult cached = CACHE.get(key);
        if (cached != null) {
            if (onLoaded != null) {
                onLoaded.accept(cached);
            }
            return;
        }

        boolean shouldSubmit = false;
        long taskGen;
        synchronized (IN_FLIGHT_LOCK) {
            taskGen = GENERATION.get();
            List<Consumer<ThumbnailResult>> callbacks = IN_FLIGHT.get(key);
            if (callbacks == null) {
                callbacks = new ArrayList<>(1);
                IN_FLIGHT.put(key, callbacks);
                shouldSubmit = true;
            }
            if (onLoaded != null) {
                callbacks.add(onLoaded);
            }
        }

        if (shouldSubmit) {
            submitDecodeTask(key, taskGen);
        }
    }

    /**
     * Prefetches a thumbnail into the LRU cache without a UI callback.
     */
    public static void prefetch(Path path, int targetWidth) {
        requestThumbnail(path, targetWidth, null);
    }

    /**
     * Backward-compatible helper returning the cached texture {@link Identifier}
     * if available, or scheduling a prefetch and returning the shared placeholder.
     */
    public static Identifier getOrCreateThumbnail(Path path, int targetWidth) {
        if (path == null) {
            return getPlaceholderId();
        }
        int clampedWidth = Math.max(1, targetWidth);
        ThumbnailResult cached = CACHE.get(path, clampedWidth);
        if (cached != null) {
            return cached.id();
        }
        prefetch(path, clampedWidth);
        return getPlaceholderId();
    }

    /**
     * Drains up to {@code maxPerFrame} decoded thumbnails on the render/main
     * thread, uploads them to the GPU, stores them in the LRU cache, and
     * delivers waiting callbacks.
     * <p>
     * Any queued images from stale generations are closed immediately without
     * uploading.
     */
    public static void drainPendingUploads(int maxPerFrame) {
        if (maxPerFrame <= 0) {
            return;
        }

        int uploaded = 0;
        while (uploaded < maxPerFrame) {
            DecodedThumbnail decoded = PENDING_UPLOADS.poll();
            if (decoded == null) {
                break;
            }

            if (decoded.generation != GENERATION.get()) {
                decoded.closeQuietly();
                continue;
            }

            List<Consumer<ThumbnailResult>> callbacks = null;
            synchronized (IN_FLIGHT_LOCK) {
                if (decoded.generation == GENERATION.get()) {
                    callbacks = IN_FLIGHT.remove(decoded.key);
                }
            }

            Identifier id = createThumbnailId(decoded.key);
            if (!registerTextureSafe(id, decoded.image)) {
                decoded.closeQuietly();
                continue;
            }

            ThumbnailResult result = new ThumbnailResult(id, decoded.width, decoded.height);
            CACHE.put(decoded.key, result);
            uploaded++;

            if (callbacks != null) {
                for (Consumer<ThumbnailResult> callback : callbacks) {
                    try {
                        callback.accept(result);
                    } catch (Exception e) {
                        LOGGER.warn("Thumbnail callback threw an exception for {}", decoded.key.path(), e);
                    }
                }
            }
        }
    }

    /**
     * Cancels all pending/in-flight thumbnail requests and closes any already
     * decoded images waiting for upload, while preserving the warm LRU cache.
     * Must be called on the client render/main thread.
     */
    public static void cancelPending() {
        synchronized (IN_FLIGHT_LOCK) {
            GENERATION.incrementAndGet();
            IN_FLIGHT.clear();
        }

        DecodedThumbnail decoded;
        while ((decoded = PENDING_UPLOADS.poll()) != null) {
            decoded.closeQuietly();
        }
    }

    /**
     * Evicts and releases all cached thumbnails for {@code path} across any
     * target width. Must be called on the client render/main thread.
     */
    public static void evict(Path path) {
        if (path == null) {
            return;
        }
        Path normalized = path.normalize();
        CACHE.evictByPath(normalized);

        synchronized (IN_FLIGHT_LOCK) {
            IN_FLIGHT.keySet().removeIf(k -> k.path().equals(normalized));
        }

        Iterator<DecodedThumbnail> it = PENDING_UPLOADS.iterator();
        while (it.hasNext()) {
            DecodedThumbnail pending = it.next();
            if (pending.key.path().equals(normalized)) {
                it.remove();
                pending.closeQuietly();
            }
        }
    }

    /**
     * Cancels all pending work, releases all cached GPU textures, and clears
     * the LRU cache. Must be called on the client render/main thread.
     */
    public static void clearCache() {
        cancelPending();
        CACHE.clear();
    }

    private static void submitDecodeTask(CacheKey key, long taskGen) {
        try {
            LOADER_EXECUTOR.execute(() -> decodeAndEnqueue(key, taskGen));
        } catch (Exception e) {
            LOGGER.warn("Failed to submit thumbnail decode task for {}", key.path(), e);
            clearInFlightIfCurrent(key, taskGen);
        }
    }

    private static void decodeAndEnqueue(CacheKey key, long taskGen) {
        // Generation check BEFORE opening file
        if (GENERATION.get() != taskGen) {
            return;
        }

        if (!Files.isRegularFile(key.path())) {
            clearInFlightIfCurrent(key, taskGen);
            return;
        }

        NativeImage thumb = null;
        boolean transferred = false;
        try (InputStream is = Files.newInputStream(key.path());
             NativeImage fullImg = NativeImage.read(is)) {

            if (GENERATION.get() != taskGen) {
                return;
            }

            int srcWidth = fullImg.getWidth();
            int srcHeight = fullImg.getHeight();
            ThumbnailSizing.Dimensions dims = ThumbnailSizing.calculateDimensions(
                    srcWidth, srcHeight, key.targetWidth());

            thumb = new NativeImage(dims.width(), dims.height(), true);
            fullImg.resizeSubRectTo(0, 0, srcWidth, srcHeight, thumb);

            // Generation check AFTER decoding/resizing
            if (GENERATION.get() != taskGen) {
                return;
            }

            PENDING_UPLOADS.offer(new DecodedThumbnail(key, thumb, dims.width(), dims.height(), taskGen));
            transferred = true;
        } catch (Exception e) {
            LOGGER.warn("Failed to decode thumbnail for {}", key.path(), e);
        } finally {
            if (!transferred) {
                if (thumb != null) {
                    try {
                        thumb.close();
                    } catch (Exception e) {
                        LOGGER.debug("Failed to close thumbnail NativeImage for {}", key.path(), e);
                    }
                }
                clearInFlightIfCurrent(key, taskGen);
            }
        }
    }

    private static void clearInFlightIfCurrent(CacheKey key, long taskGen) {
        synchronized (IN_FLIGHT_LOCK) {
            if (GENERATION.get() == taskGen) {
                IN_FLIGHT.remove(key);
            }
        }
    }

    private static Identifier createThumbnailId(CacheKey key) {
        Path fileName = key.path().getFileName();
        String sanitized = fileName != null
                ? fileName.toString().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_")
                : "img";
        return Identifier.fromNamespaceAndPath(
                "screenshot-manager-enhanced",
                "thumb/" + key.targetWidth() + "_" + TEXTURE_COUNTER.incrementAndGet() + "_" + sanitized);
    }

    private static boolean registerTextureSafe(Identifier id, NativeImage image) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getTextureManager() == null) {
                return false;
            }
            DynamicTexture texture = DynamicTextureFactory.create(id, image);
            if (texture == null) {
                return false;
            }
            mc.getTextureManager().register(id, texture);
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to register dynamic texture {}", id, e);
            return false;
        }
    }

    private static void releaseTexture(Identifier id) {
        if (id == null) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getTextureManager() != null) {
                mc.getTextureManager().release(id);
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to release texture {}", id, e);
        }
    }
}
