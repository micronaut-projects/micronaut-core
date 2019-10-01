package io.micronaut.cache;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Transforms a synchronous cache into one that meets the asynchronous
 * contract while still running operations on the same thread.
 *
 * @param <C> The cache type
 * @author James Kleeh
 * @since 1.3.0
 */
@Internal
public class DelegatingAsyncBlockingCache<C> implements AsyncCache<C> {

    private final SyncCache<C> delegate;

    /**
     * @param delegate The delegate blocking cache
     */
    public DelegatingAsyncBlockingCache(SyncCache<C> delegate) {
        this.delegate = delegate;
    }
    
    @Override
    public <T> CompletableFuture<Optional<T>> get(Object key, Argument<T> requiredType) {
        try {
            return CompletableFuture.completedFuture(delegate.get(key, requiredType));
        } catch (Exception e) {
            return handleException(e);
        }
    }

    @Override
    public <T> CompletableFuture<T> get(Object key, Argument<T> requiredType, Supplier<T> supplier) {
        try {
            return CompletableFuture.completedFuture(delegate.get(key, requiredType, supplier));
        } catch (Exception e) {
            return handleException(e);
        }
    }

    @Override
    public <T> CompletableFuture<Optional<T>> putIfAbsent(Object key, T value) {
        try {
            return CompletableFuture.completedFuture(delegate.putIfAbsent(key, value));
        } catch (Exception e) {
            return handleException(e);
        }
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public C getNativeCache() {
        return delegate.getNativeCache();
    }

    @Override
    public CompletableFuture<Boolean> put(Object key, Object value) {
        try {
            delegate.put(key, value);
            return CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            return handleException(e);
        }
    }

    @Override
    public CompletableFuture<Boolean> invalidate(Object key) {
        try {
            delegate.invalidate(key);
            return CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            return handleException(e);
        }
    }

    @Override
    public CompletableFuture<Boolean> invalidateAll() {
        try {
            delegate.invalidateAll();
            return CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            return handleException(e);
        }
    }

    private <T> CompletableFuture<T> handleException(Exception e) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(e);
        return future;
    }

}
