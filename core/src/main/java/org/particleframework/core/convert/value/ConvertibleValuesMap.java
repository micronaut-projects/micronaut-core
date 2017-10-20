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
package org.particleframework.core.convert.value;

import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.type.Argument;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An implementation of {@link ConvertibleValues} backed by a map
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ConvertibleValuesMap<V> implements ConvertibleValues<V> {

    protected final Map<CharSequence, V> map;
    private final ConversionService<?> conversionService;

    public ConvertibleValuesMap() {
        this(new LinkedHashMap<>(), ConversionService.SHARED);
    }

    public ConvertibleValuesMap(Map<CharSequence, V> map) {
        this(map, ConversionService.SHARED);
    }

    public ConvertibleValuesMap(Map<CharSequence, V> map, ConversionService<?> conversionService) {
        this.map = map;
        this.conversionService = conversionService;
    }

    @Override
    public <T> Optional<T> get(CharSequence name, Class<T> requiredType) {
        V value = map.get(name);
        if(value != null) {
            return conversionService.convert(value, requiredType);
        }
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> get(CharSequence name, Argument<T> requiredType) {
        V value = map.get(name);
        if(value != null) {
            return conversionService.convert(value, requiredType.getType(), ConversionContext.of(requiredType));
        }
        return Optional.empty();
    }

    @Override
    public Set<String> getNames() {
        return map.keySet().stream().map(CharSequence::toString).collect(Collectors.toSet());
    }
}
