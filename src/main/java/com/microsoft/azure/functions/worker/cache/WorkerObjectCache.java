package com.microsoft.azure.functions.worker.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.microsoft.azure.functions.cache.CacheKey;
import com.microsoft.azure.functions.cache.ObjectCache;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Uses Guava's Cache<WorkerObjectCacheKey<K>, Object> with weak values
 * and eviction policies.
 */
public class WorkerObjectCache<K extends CacheKey> implements ObjectCache<K, Object> {

    private final Cache<WorkerObjectCacheKey<K>, Object> guavaCache;

    @SuppressWarnings("unchecked")
    public WorkerObjectCache() {
        // Eviction: max 1000 entries,
        // automatically remove if not accessed for 5 minutes,
        // store values as weak references.
        this.guavaCache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterAccess(5, TimeUnit.MINUTES)
                .weakValues()
                .build();
    }

    @Override
    public Object computeIfAbsent(Class<?> namespace, K key, Supplier<Object> creator) {
        WorkerObjectCacheKey<K> composite = new WorkerObjectCacheKey<>(namespace, key);

        // Guava doesn't have a direct "computeIfAbsent", but we can do get(key, Supplier).
        try {
            return guavaCache.get(composite, creator::get);
        } catch (Exception e) {
            throw new RuntimeException("Failed to computeIfAbsent for " + composite, e);
        }
    }

    @Override
    public Object get(Class<?> namespace, K key) {
        return guavaCache.getIfPresent(new WorkerObjectCacheKey<>(namespace, key));
    }

    @Override
    public Object remove(Class<?> namespace, K key) {
        WorkerObjectCacheKey<K> composite = new WorkerObjectCacheKey<>(namespace, key);
        Object oldVal = guavaCache.getIfPresent(composite);
        guavaCache.invalidate(composite);
        return oldVal;
    }
}
