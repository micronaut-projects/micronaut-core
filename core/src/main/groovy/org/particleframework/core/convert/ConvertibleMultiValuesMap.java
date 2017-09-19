/*
 * Copyright 2017 original authors
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
package org.particleframework.core.convert;

import java.util.*;
import java.util.stream.Collectors;

/**
 * An implementation of {@link ConvertibleMultiValues} that uses a backing {@link LinkedHashMap}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ConvertibleMultiValuesMap<V> implements ConvertibleMultiValues<V> {
    protected final Map<CharSequence, List<V>> values;
    private final ConversionService<?> conversionService;

    public ConvertibleMultiValuesMap() {
        this(new LinkedHashMap<>(), ConversionService.SHARED);
    }
    public ConvertibleMultiValuesMap(Map<CharSequence, List<V>> values) {
        this(values, ConversionService.SHARED);
    }
    public ConvertibleMultiValuesMap(Map<CharSequence, List<V>> values, ConversionService<?> conversionService) {
        this.values = values;
        this.conversionService = conversionService;
    }

    @Override
    public <T> Optional<T> get(CharSequence name, Class<T> requiredType) {
        List<V> values = getAll(name);
        if(!values.isEmpty()) {
            V value = values.get(0);
            return conversionService.convert(value, requiredType);
        }
        return Optional.empty();
    }

    @Override
    public List<V> getAll(CharSequence name) {
        List<V> value = values.get(name);
        if(value != null) {
            return Collections.unmodifiableList(value);
        }
        else {
            return Collections.emptyList();
        }
    }

    @Override
    public V get(CharSequence name) {
        List<V> all = getAll(name);
        if(all.isEmpty()) {
            return null;
        }
        return all.get(0);
    }

    @Override
    public Set<String> getNames() {
        return values.keySet().stream().map(CharSequence::toString).collect(Collectors.toSet());
    }
}
