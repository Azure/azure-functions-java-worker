package com.microsoft.azure.functions.worker.cache;

import com.microsoft.azure.functions.cache.CacheKey;

import java.util.Objects;

/**
 * A composite key combining (Class<?> namespace, K extends CacheKey).
 */
public class WorkerObjectCacheKey<K extends CacheKey> {
    private final Class<?> namespace;
    private final K key;

    public WorkerObjectCacheKey(Class<?> namespace, K key) {
        this.namespace = namespace;
        this.key = key;
    }

    public Class<?> getNamespace() {
        return namespace;
    }

    public K getKey() {
        return key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorkerObjectCacheKey)) return false;
        WorkerObjectCacheKey<?> that = (WorkerObjectCacheKey<?>) o;
        return Objects.equals(namespace, that.namespace) &&
                Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, key);
    }

    @Override
    public String toString() {
        return "WorkerObjectCacheKey{" +
                "namespace=" + namespace +
                ", key=" + key +
                '}';
    }
}
