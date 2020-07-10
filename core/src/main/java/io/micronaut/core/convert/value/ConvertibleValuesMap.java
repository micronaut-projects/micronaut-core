/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.core.convert.value;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An implementation of {@link ConvertibleValues} backed by a map.
 *
 * @author Graeme Rocher
 * @param <V> generic value
 * @since 1.0
 */
public class ConvertibleValuesMap<V> implements ConvertibleValues<V> {

    protected final Map<? extends CharSequence, V> map;
    private final ConversionService<?> conversionService;

    /**
     * Constructor.
     */
    public ConvertibleValuesMap() {
        this(new LinkedHashMap<>(), ConversionService.SHARED);
    }

    /**
     * Constructor.
     * @param map map of values.
     */
    public ConvertibleValuesMap(Map<? extends CharSequence, V> map) {
        this(map, ConversionService.SHARED);
    }

    /**
     * Constructor.
     * @param map map of values.
     * @param conversionService conversionService
     */
    public ConvertibleValuesMap(Map<? extends CharSequence, V> map, ConversionService<?> conversionService) {
        this.map = map;
        this.conversionService = conversionService;
    }

    @Nullable
    @Override
    public V getValue(CharSequence name) {
        return name != null ? map.get(name) : null;
    }

    @Override
    public boolean contains(String name) {
        return name != null && map.containsKey(name);
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        V value = map.get(name);
        if (value != null) {
            return conversionService.convert(value, conversionContext);
        }
        return Optional.empty();
    }

    @Override
    public Set<String> names() {
        return map.keySet().stream().map(CharSequence::toString).collect(Collectors.toSet());
    }

    @Override
    public Collection<V> values() {
        return Collections.unmodifiableCollection(map.values());
    }

    /**
     * An empty {@link ConvertibleValuesMap}.
     *
     * @param <V> The generic type
     * @return The empty {@link ConvertibleValuesMap}
     */
    @SuppressWarnings("unchecked")
    public static <V> ConvertibleValues<V> empty() {
        return EMPTY;
    }
}
