/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.core.annotation;

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Base for generated annotation member maps. Immutable instances preserve their generated
 * interface at metadata read boundaries; live adapters retain ordinary map behavior.
 */
@Internal
public abstract class AnnotationMap implements Map<CharSequence, Object> {
    private final Map<CharSequence, Object> delegate;

    private final boolean immutable;

    /**
     * Construct a live view or immutable snapshot.
     *
     * @param delegate The member values
     * @param immutable Whether to take an immutable snapshot
     */
    @UsedByGeneratedCode
    protected AnnotationMap(Map<CharSequence, Object> delegate, boolean immutable) {
        this.delegate = immutable ? Map.copyOf(delegate) : Objects.requireNonNull(delegate);
        this.immutable = immutable;
    }

    /**
     * Access the backing map when initializing generated fields.
     *
     * @return The backing map, frozen before cached fields are initialized
     */
    @UsedByGeneratedCode
    protected final Map<CharSequence, Object> getDelegate() {
        return delegate;
    }

    /**
     * Preserve the generated interface only when all map operations are immutable.
     *
     * @param values The member values
     * @return A read-only view
     */
    public static Map<CharSequence, Object> readOnly(Map<CharSequence, Object> values) {
        return values instanceof AnnotationMap map && map.immutable
            ? values : Collections.unmodifiableMap(values);
    }

    @Override
    public final int size() {
        return delegate.size();
    }

    @Override
    public final boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public final boolean containsKey(@Nullable Object key) {
        return delegate.containsKey(key);
    }

    @Override
    public final boolean containsValue(@Nullable Object value) {
        return delegate.containsValue(value);
    }

    @Override
    public final @Nullable Object get(@Nullable Object key) {
        return delegate.get(key);
    }

    @Override
    public final @Nullable Object put(CharSequence key, Object value) {
        return delegate.put(key, value);
    }

    @Override
    public final @Nullable Object remove(@Nullable Object key) {
        return delegate.remove(key);
    }

    @Override
    public final void putAll(Map<? extends CharSequence, ?> values) {
        delegate.putAll(values);
    }

    @Override
    public final void clear() {
        delegate.clear();
    }

    @Override
    public final Set<CharSequence> keySet() {
        return delegate.keySet();
    }

    @Override
    public final Collection<Object> values() {
        return delegate.values();
    }

    @Override
    public final Set<Entry<CharSequence, Object>> entrySet() {
        return delegate.entrySet();
    }

    @Override
    public final Object getOrDefault(@Nullable Object key, Object fallback) {
        return delegate.getOrDefault(key, fallback);
    }

    @Override
    public final void forEach(BiConsumer<? super CharSequence, ? super Object> action) {
        delegate.forEach(action);
    }

    @Override
    public final void replaceAll(BiFunction<? super CharSequence, ? super Object, ?> function) {
        delegate.replaceAll(function);
    }

    @Override
    public final @Nullable Object putIfAbsent(CharSequence key, Object value) {
        return delegate.putIfAbsent(key, value);
    }

    @Override
    public final boolean remove(@Nullable Object key, @Nullable Object value) {
        return delegate.remove(key, value);
    }

    @Override
    public final boolean replace(CharSequence key, Object oldValue, Object value) {
        return delegate.replace(key, oldValue, value);
    }

    @Override
    public final @Nullable Object replace(CharSequence key, Object value) {
        return delegate.replace(key, value);
    }

    @Override
    public final @Nullable Object computeIfAbsent(CharSequence key, Function<? super CharSequence, ?> function) {
        return delegate.computeIfAbsent(key, function);
    }

    @Override
    public final @Nullable Object computeIfPresent(CharSequence key, BiFunction<? super CharSequence, ? super Object, ?> function) {
        return delegate.computeIfPresent(key, function);
    }

    @Override
    public final @Nullable Object compute(CharSequence key, BiFunction<? super CharSequence, ? super @Nullable Object, ?> function) {
        return delegate.compute(key, function);
    }

    @Override
    public final @Nullable Object merge(CharSequence key, Object value, BiFunction<? super Object, ? super Object, ?> function) {
        return delegate.merge(key, value, function);
    }

    @Override
    public final boolean equals(@Nullable Object other) {
        return other == this || delegate.equals(other);
    }

    @Override
    public final int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public final String toString() {
        return delegate.toString();
    }
}
