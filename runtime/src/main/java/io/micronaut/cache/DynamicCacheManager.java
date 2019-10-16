package io.micronaut.cache;

import javax.annotation.Nonnull;

/**
 * <p>A contract for a cache manager that does not have pre-defined caches.</p>
 *
 * @param <C> The native cache implementation
 *
 * @author James Kleeh
 * @since 1.3.0
 */
public interface DynamicCacheManager<C> {

    /**
     * Retrieve a cache for the given name. If the cache does not previously exist, a new one will be created.
     * The cache instance should not be cached internally because the cache manager will maintain the instance
     * for future requests.
     *
     * @param name The name of the cache
     * @return The {@link SyncCache} instance
     */
    @Nonnull
    SyncCache<C> getCache(String name);
}
