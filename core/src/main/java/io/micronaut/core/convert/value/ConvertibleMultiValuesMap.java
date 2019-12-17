/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.core.convert.value;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An implementation of {@link ConvertibleMultiValues} that uses a backing {@link LinkedHashMap}.
 *
 * @param <V> The generic value
 * @author Graeme Rocher
 * @since 1.0
 */
public class ConvertibleMultiValuesMap<V> implements ConvertibleMultiValues<V> {
    public static final ConvertibleMultiValues EMPTY = new ConvertibleMultiValuesMap<>(Collections.emptyMap());

    protected final Map<CharSequence, List<V>> values;
    private final ConversionService<?> conversionService;

    /**
     * Construct an empty {@link ConvertibleValuesMap}.
     */
    public ConvertibleMultiValuesMap() {
        this(new LinkedHashMap<>(), ConversionService.SHARED);
    }

    /**
     * Construct a {@link ConvertibleValuesMap} from the given map.
     *
     * @param values The map
     */
    public ConvertibleMultiValuesMap(Map<CharSequence, List<V>> values) {
        this(values, ConversionService.SHARED);
    }

    /**
     * Construct a {@link ConvertibleValuesMap} from the given map and conversion service.
     *
     * @param values            The map
     * @param conversionService The conversion service
     */
    public ConvertibleMultiValuesMap(Map<CharSequence, List<V>> values, ConversionService<?> conversionService) {
        this.values = wrapValues(values);
        this.conversionService = conversionService;
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        List<V> values = getAll(name);
        if (!values.isEmpty()) {
            boolean hasSingleEntry = values.size() == 1;
            if (hasSingleEntry) {
                final V v = values.iterator().next();
                if (conversionContext.getArgument().getType().isInstance(v)) {
                    return Optional.of((T) v);
                } else {
                    return conversionService.convert(v, conversionContext);
                }
            } else {

                Optional<T> converted = conversionService.convert(values, conversionContext);
                boolean hasValue = converted.isPresent();
                if (!hasValue && hasSingleEntry) {
                    return conversionService.convert(values.get(0), conversionContext);
                } else if (hasValue && hasSingleEntry) {
                    T result = converted.get();
                    if (result instanceof Collection && ((Collection) result).isEmpty()) {
                        return conversionService.convert(values.get(0), conversionContext);
                    } else if (result instanceof Optional && !((Optional) result).isPresent()) {
                        return conversionService.convert(values.get(0), conversionContext);
                    } else {
                        return converted;
                    }
                } else {
                    return converted;
                }
            }
        } else {
            Argument<T> argument = conversionContext.getArgument();
            if (Map.class.isAssignableFrom(argument.getType())) {
                Argument valueType = argument.getTypeVariable("V").orElse(Argument.OBJECT_ARGUMENT);
                Map map = subMap(name.toString(), valueType);
                if (map.isEmpty()) {
                    return Optional.empty();
                } else {
                    return Optional.of((T) map);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<V> getAll(CharSequence name) {
        List<V> value = values.get(name);
        if (value != null) {
            return Collections.unmodifiableList(value);
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public V get(CharSequence name) {
        List<V> all = getAll(name);
        if (all.isEmpty()) {
            return null;
        }
        return all.get(0);
    }

    @Override
    public Set<String> names() {
        return values.keySet().stream().map(CharSequence::toString).collect(Collectors.toSet());
    }

    @Override
    public Collection<List<V>> values() {
        return Collections.unmodifiableCollection(values.values());
    }

    /**
     * Wraps the values (by default in an unmodifiable map).
     * @param values The values
     * @return The wrapped values.
     */
    protected Map<CharSequence, List<V>> wrapValues(Map<CharSequence, List<V>> values) {
        return Collections.unmodifiableMap(values);
    }

}
