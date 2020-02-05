package io.micronaut.cache;

import io.micronaut.core.type.Argument;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class DynamicCache implements SyncCache<Map> {

    @NonNull
    @Override
    public <T> Optional<T> get(@NonNull Object key, @NonNull Argument<T> requiredType) {
        return Optional.empty();
    }

    @Override
    public <T> T get(@NonNull Object key, @NonNull Argument<T> requiredType, @NonNull Supplier<T> supplier) {
        return null;
    }

    @NonNull
    @Override
    public <T> Optional<T> putIfAbsent(@NonNull Object key, @NonNull T value) {
        return Optional.empty();
    }

    @Override
    public void put(@NonNull Object key, @NonNull Object value) {

    }

    @Override
    public void invalidate(@NonNull Object key) {

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
