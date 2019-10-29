package io.micronaut.cache;

import io.micronaut.core.type.Argument;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class DynamicCache implements SyncCache<Map> {

    @Nonnull
    @Override
    public <T> Optional<T> get(@Nonnull Object key, @Nonnull Argument<T> requiredType) {
        return Optional.empty();
    }

    @Override
    public <T> T get(@Nonnull Object key, @Nonnull Argument<T> requiredType, @Nonnull Supplier<T> supplier) {
        return null;
    }

    @Nonnull
    @Override
    public <T> Optional<T> putIfAbsent(@Nonnull Object key, @Nonnull T value) {
        return Optional.empty();
    }

    @Override
    public void put(@Nonnull Object key, @Nonnull Object value) {

    }

    @Override
    public void invalidate(@Nonnull Object key) {

    }

    @Override
    public void invalidateAll() {

    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public Map getNativeCache() {
        return null;
    }
}
